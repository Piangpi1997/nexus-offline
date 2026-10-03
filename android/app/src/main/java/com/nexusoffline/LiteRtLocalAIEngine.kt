package com.nexusoffline

import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.MessageCallback
import java.io.File
import java.util.concurrent.Executors
import org.json.JSONArray
import org.json.JSONObject

internal interface LocalAIRuntimeFactory {
    fun create(modelPath: String, cacheDirectory: String): LocalAIRuntimeEngine
}

internal interface LocalAIRuntimeEngine {
    fun initialize()
    fun createConversation(): LocalAIRuntimeConversation
    fun close()
}

internal interface LocalAIRuntimeConversation {
    fun sendMessageAsync(prompt: String, callback: LocalAIRuntimeMessageCallback)
    fun close()
}

internal interface LocalAIRuntimeMessageCallback {
    fun onMessage(text: String)
    fun onDone()
    fun onError(error: Throwable)
}

internal object NativeLocalAIRuntimeFactory : LocalAIRuntimeFactory {
    override fun create(modelPath: String, cacheDirectory: String): LocalAIRuntimeEngine =
        NativeLocalAIRuntimeEngine(modelPath, cacheDirectory)
}

private class NativeLocalAIRuntimeEngine(modelPath: String, cacheDirectory: String) : LocalAIRuntimeEngine {
    private val engine = Engine(EngineConfig(modelPath = modelPath, backend = Backend.CPU(), cacheDir = cacheDirectory))

    override fun initialize() = engine.initialize()
    override fun createConversation(): LocalAIRuntimeConversation = NativeLocalAIRuntimeConversation(engine.createConversation())
    override fun close() = engine.close()
}

private class NativeLocalAIRuntimeConversation(private val conversation: Conversation) : LocalAIRuntimeConversation {
    override fun sendMessageAsync(prompt: String, callback: LocalAIRuntimeMessageCallback) {
        conversation.sendMessageAsync(prompt, object : MessageCallback {
            override fun onMessage(message: Message) {
                val text = message.contents.contents.filterIsInstance<Content.Text>().joinToString("") { it.text }
                if (text.isNotEmpty()) callback.onMessage(text)
            }

            override fun onDone() = callback.onDone()
            override fun onError(throwable: Throwable) = callback.onError(throwable)
        })
    }

    override fun close() = conversation.close()
}

/** LiteRT-LM CPU adapter. Model files remain local; there is intentionally no network code. */
internal class LiteRtLocalAIEngine(
    private val modelStore: LocalAIModelStore,
    private val cacheDirectory: File,
    private val deviceAbis: List<String>,
    private val availableStorageBytes: () -> Long = { 0L },
    private val availableRamBytes: () -> Long? = { null },
    private val totalRamBytes: () -> Long? = { null },
    private val runtimeFactory: LocalAIRuntimeFactory = NativeLocalAIRuntimeFactory,
    private val emit: (String, JSONObject) -> Unit
) : LocalAIEngine {
    private val lock = Any()
    private val executor = Executors.newSingleThreadExecutor { runnable -> Thread(runnable, "nexus-local-ai").apply { isDaemon = true } }
    private val generationFence = LocalAIGenerationFence()
    private var engine: LocalAIRuntimeEngine? = null
    private var conversation: LocalAIRuntimeConversation? = null
    private var stagedPlaintextModel: File? = null
    private var loadedModelId: String? = null
    private var state = "IDLE"
    private var lastError: String? = null
    private var generating = false
    private var closed = false

    override fun initializeRuntime(): String =
        if (runtimeAvailable()) "RUNTIME_PRESENT_LOAD_ON_DEMAND" else "UNSUPPORTED_DEVICE_ABI_OR_RUNTIME"

    override fun getRuntimeStatus(): LocalAIStatus {
        val availableAbis = deviceAbis.filter { it in SUPPORTED_ABIS }
        val runtimeAvailable = runtimeAvailable()
        val availableRam = runCatching { availableRamBytes() }.getOrNull()
        val availableStorage = runCatching { availableStorageBytes() }.getOrDefault(0L)
        val currentState: String
        val currentModel: String?
        val error: String?
        synchronized(lock) {
            currentState = state
            currentModel = loadedModelId
            error = lastError
        }
        val models = modelStore.listModels().map { model ->
            val inspection = model.inspection
            val compatibility = when {
                inspection == null -> model.inspectionError ?: "MODEL_INSPECTION_FAILED"
                !runtimeAvailable -> "RUNTIME_UNAVAILABLE"
                availableRam != null && inspection.estimatedRamBytes > availableRam -> "LOW_AVAILABLE_RAM_ESTIMATE"
                else -> inspection.compatibility
            }
            LocalAIModelInfo(
                id = model.id,
                displayName = model.displayName,
                license = inspection?.license,
                requiredRamBytes = inspection?.estimatedRamBytes,
                requiredStorageBytes = model.sizeBytes,
                availableRamBytes = availableRam,
                format = inspection?.format ?: "Unrecognized / corrupt",
                formatVersion = inspection?.formatVersion,
                architecture = inspection?.architecture,
                contextLength = inspection?.contextLength,
                compatibility = compatibility,
                supportedAbis = availableAbis
            )
        }
        val message = when {
            models.isEmpty() -> LOCAL_AI_MODEL_NOT_INSTALLED
            !runtimeAvailable -> "UNSUPPORTED_DEVICE_ABI_OR_RUNTIME"
            currentState == "READY" -> "MODEL_LOADED"
            error != null -> error
            else -> currentState
        }
        return LocalAIStatus(
            runtimeInstalled = runtimeAvailable,
            runtimeName = if (runtimeAvailable) "LiteRT-LM Android 0.17.1 · CPU" else null,
            loadedModelId = currentModel,
            installedModels = models,
            message = message,
            state = currentState,
            errorCode = error,
            supportedAbis = availableAbis,
            availableStorageBytes = availableStorage,
            availableRamBytes = availableRam,
            totalRamBytes = runCatching { totalRamBytes() }.getOrNull(),
            modelStatus = when {
                models.isEmpty() -> LOCAL_AI_MODEL_NOT_INSTALLED
                currentModel != null -> "MODEL_LOADED"
                else -> "MODEL_NOT_LOADED"
            }
        )
    }

    override fun inspectModel(modelId: String): LocalAIModelInfo? =
        getRuntimeStatus().installedModels.firstOrNull { it.id == modelId }

    override fun importModel(sourceDescription: String): String =
        "MODEL_IMPORT_REQUIRES_DOCUMENT_PICKER"

    override fun registerImportedModel(model: ImportedLocalAIModel): String {
        val stored = modelStore.findModel(model.id) ?: return "MODEL_NOT_INSTALLED"
        if (stored.file.canonicalFile != model.file.canonicalFile || stored.sizeBytes != model.sizeBytes || stored.inspection?.structurallyCompatible != true) return "MODEL_REGISTRATION_FAILED"
        lastError = null
        emitState("MODEL_IMPORTED")
        return "MODEL_IMPORTED"
    }

    override fun loadModel(modelId: String): String {
        if (!runtimeAvailable()) return "UNSUPPORTED_DEVICE_ABI_OR_RUNTIME"
        if (!modelId.matches(Regex("^[a-f0-9]{64}$"))) return "BAD_MODEL_ID"
        val model = modelStore.findModel(modelId) ?: return "MODEL_NOT_INSTALLED"
        if (model.inspection?.structurallyCompatible != true) return model.inspectionError ?: "UNSUPPORTED_MODEL_CONTENT"
        val availableRam = runCatching { availableRamBytes() }.getOrNull()
        if (availableRam != null && model.inspection.estimatedRamBytes > availableRam) return "MODEL_MEMORY_ESTIMATE_EXCEEDS_AVAILABLE_RAM"
        val epoch: Long
        synchronized(lock) {
            if (closed) return "ENGINE_CLOSED"
            if (state == "LOADING" || generating) return "ENGINE_BUSY"
            if (loadedModelId == modelId && state == "READY") return "MODEL_ALREADY_LOADED"
            closeRuntimeLocked()
            state = "LOADING"
            lastError = null
            epoch = generationFence.nextToken()
        }
        emitState("MODEL_LOADING")
        executor.execute {
            var createdEngine: LocalAIRuntimeEngine? = null
            var createdConversation: LocalAIRuntimeConversation? = null
            var stagedFile: File? = null
            try {
                if (!model.file.isFile || model.sizeBytes <= 0L) throw IllegalStateException("MODEL_FILE_INVALID")
                if (deviceAbis.none { it in SUPPORTED_ABIS }) throw IllegalStateException("UNSUPPORTED_DEVICE_ABI_OR_RUNTIME")
                cacheDirectory.mkdirs()
                stagedFile = modelStore.stageModelForRuntime(model.id)
                createdEngine = runtimeFactory.create(stagedFile.absolutePath, cacheDirectory.absolutePath)
                createdEngine.initialize()
                createdConversation = createdEngine.createConversation()
                val accepted = synchronized(lock) {
                    if (closed || !generationFence.isCurrent(epoch)) false else {
                        engine = createdEngine
                        conversation = createdConversation
                        stagedPlaintextModel = stagedFile
                        loadedModelId = model.id
                        state = "READY"
                        lastError = null
                        true
                    }
                }
                if (accepted) emitState("MODEL_LOADED") else {
                    runCatching { createdConversation.close() }
                    runCatching { createdEngine.close() }
                }
                if (accepted) stagedFile = null
            } catch (error: Throwable) {
                runCatching { createdConversation?.close() }
                runCatching { createdEngine?.close() }
                val code = when (error) {
                    is OutOfMemoryError -> "MODEL_MEMORY_LIMIT"
                    else -> (error.message ?: "").takeIf { it.matches(Regex("^[A-Z0-9_]{2,64}$")) } ?: "MODEL_LOAD_FAILED"
                }
                val shouldReport = synchronized(lock) {
                    if (generationFence.isCurrent(epoch) && !closed) {
                        state = "ERROR"
                        lastError = code
                        loadedModelId = null
                        engine = null
                        conversation = null
                        true
                    } else false
                }
                if (shouldReport) emitState(code)
            } finally {
                stagedFile?.let(modelStore::cleanupRuntimeStage)
            }
        }
        return "MODEL_LOADING"
    }

    override fun generate(prompt: String): String {
        if (prompt.isBlank() || prompt.length > MAX_PROMPT_CHARS) return "INVALID_PROMPT"
        val activeConversation: LocalAIRuntimeConversation
        val epoch: Long
        synchronized(lock) {
            if (closed) return "ENGINE_CLOSED"
            if (generating) return "GENERATION_BUSY"
            if (state != "READY" || conversation == null) return "MODEL_NOT_LOADED"
            generating = true
            state = "GENERATING"
            lastError = null
            epoch = generationFence.nextToken()
            activeConversation = conversation!!
        }
        emitState("GENERATION_STARTED")
        executor.execute {
            try {
                val callback = object : LocalAIRuntimeMessageCallback {
                    override fun onMessage(text: String) {
                        if (!generationFence.isCurrent(epoch)) return
                        emit("localAIGenerationChunk", JSONObject().put("text", text).put("replace", true))
                    }

                    override fun onDone() {
                        synchronized(lock) {
                            if (!generationFence.isCurrent(epoch)) return
                            generating = false
                            state = "READY"
                        }
                        emitState("GENERATION_DONE")
                    }

                    override fun onError(error: Throwable) {
                        synchronized(lock) {
                            if (!generationFence.isCurrent(epoch)) return
                            generating = false
                            state = "ERROR"
                            lastError = "GENERATION_FAILED"
                        }
                        emitState("GENERATION_FAILED")
                    }
                }
                val started = synchronized(lock) {
                    if (closed || !generating || !generationFence.isCurrent(epoch) || conversation !== activeConversation) false
                    else {
                        activeConversation.sendMessageAsync(prompt, callback)
                        true
                    }
                }
                if (!started) return@execute
            } catch (_: Throwable) {
                synchronized(lock) {
                    if (!generationFence.isCurrent(epoch)) return@execute
                    generating = false
                    state = "ERROR"
                    lastError = "GENERATION_FAILED"
                }
                emitState("GENERATION_FAILED")
            }
        }
        return "GENERATION_STARTED"
    }

    override fun cancelGeneration(): Boolean {
        val oldConversation: LocalAIRuntimeConversation
        val oldEngine: LocalAIRuntimeEngine
        val oldStage: File?
        synchronized(lock) {
            if (!generating || conversation == null || engine == null) return false
            generationFence.invalidate()
            generating = false
            state = "CANCELLED"
            loadedModelId = null
            oldConversation = conversation!!
            oldEngine = engine!!
            oldStage = stagedPlaintextModel
            conversation = null
            engine = null
            stagedPlaintextModel = null
        }
        closeRuntimeAsync(oldConversation, oldEngine, oldStage, "nexus-local-ai-cancel")
        emitState("GENERATION_CANCELLED_MODEL_UNLOADED")
        return true
    }

    override fun unloadModel(): Boolean {
        val oldConversation: LocalAIRuntimeConversation?
        val oldEngine: LocalAIRuntimeEngine?
        val oldStage: File?
        val closeAsynchronously: Boolean
        synchronized(lock) {
            val hadRuntime = engine != null || conversation != null || generating || state == "LOADING"
            if (!hadRuntime) return false
            generationFence.invalidate()
            closeAsynchronously = generating
            generating = false
            loadedModelId = null
            state = "IDLE"
            lastError = null
            oldConversation = conversation
            oldEngine = engine
            oldStage = stagedPlaintextModel
            conversation = null
            engine = null
            stagedPlaintextModel = null
        }
        if (closeAsynchronously) closeRuntimeAsync(oldConversation, oldEngine, oldStage, "nexus-local-ai-unload")
        else closeRuntime(oldConversation, oldEngine, oldStage)
        emitState("MODEL_UNLOADED")
        return true
    }

    override fun releaseForMemoryPressure() {
        val oldConversation: LocalAIRuntimeConversation?
        val oldEngine: LocalAIRuntimeEngine?
        val oldStage: File?
        val shouldEmit: Boolean
        synchronized(lock) {
            generationFence.invalidate()
            generating = false
            loadedModelId = null
            shouldEmit = !closed
            if (!closed) state = "IDLE"
            lastError = null
            oldConversation = conversation
            oldEngine = engine
            oldStage = stagedPlaintextModel
            conversation = null
            engine = null
            stagedPlaintextModel = null
        }
        // This hook is called on a dedicated worker by the Activity. Close native resources first,
        // then drain a concurrent load task before deleting any remaining private plaintext stage.
        closeRuntime(oldConversation, oldEngine, oldStage)
        runCatching { executor.submit { }.get() }
        modelStore.cleanupPlaintextStageCache()
        if (shouldEmit) emitState("MEMORY_PRESSURE_MODEL_RELEASED")
    }

    override fun deleteModel(modelId: String): String {
        if (!modelId.matches(Regex("^[a-f0-9]{64}$"))) return "BAD_MODEL_ID"
        synchronized(lock) {
            if (loadedModelId == modelId || generating || state == "LOADING") return "MODEL_MUST_BE_UNLOADED"
        }
        if (modelStore.findModel(modelId) == null) return "MODEL_NOT_INSTALLED"
        return if (modelStore.deleteModel(modelId)) {
            lastError = null
            emitState("MODEL_DELETED")
            "MODEL_DELETED"
        } else "MODEL_DELETE_FAILED"
    }

    override fun close() {
        val oldConversation: LocalAIRuntimeConversation?
        val oldEngine: LocalAIRuntimeEngine?
        val oldStage: File?
        val closeAsynchronously: Boolean
        synchronized(lock) {
            if (closed) return
            closed = true
            generationFence.invalidate()
            closeAsynchronously = generating
            generating = false
            loadedModelId = null
            state = "CLOSED"
            lastError = null
            oldConversation = conversation
            oldEngine = engine
            oldStage = stagedPlaintextModel
            conversation = null
            engine = null
            stagedPlaintextModel = null
        }
        executor.shutdownNow()
        if (closeAsynchronously) closeRuntimeAsync(oldConversation, oldEngine, oldStage, "nexus-local-ai-close")
        else closeRuntime(oldConversation, oldEngine, oldStage)
    }

    private fun closeRuntimeLocked() {
        val oldConversation = conversation
        val oldEngine = engine
        val oldStage = stagedPlaintextModel
        conversation = null
        engine = null
        stagedPlaintextModel = null
        closeRuntime(oldConversation, oldEngine, oldStage)
    }

    private fun closeRuntimeAsync(conversation: LocalAIRuntimeConversation?, engine: LocalAIRuntimeEngine?, stage: File?, threadName: String) {
        Thread({ closeRuntime(conversation, engine, stage) }, threadName).apply { isDaemon = true }.start()
    }

    private fun closeRuntime(conversation: LocalAIRuntimeConversation?, engine: LocalAIRuntimeEngine?, stage: File? = null) {
        conversation?.let { runCatching { it.close() } }
        engine?.let { runCatching { it.close() } }
        modelStore.cleanupRuntimeStage(stage)
    }

    private fun emitState(code: String) {
        val snapshot = getRuntimeStatus()
        emit("localAIState", JSONObject()
            .put("code", code)
            .put("status", snapshot.message)
            .put("state", snapshot.state)
            .put("runtimeInstalled", snapshot.runtimeInstalled)
            .put("loadedModelId", snapshot.loadedModelId ?: JSONObject.NULL)
            .put("errorCode", snapshot.errorCode ?: JSONObject.NULL)
            .put("models", JSONArray().also { out -> snapshot.installedModels.forEach { out.put(JSONObject().put("id", it.id).put("displayName", it.displayName).put("sizeBytes", it.requiredStorageBytes ?: 0L)) } }))
    }

    private fun runtimeAvailable(): Boolean = deviceAbis.any { it in SUPPORTED_ABIS } && runCatching {
        Class.forName("com.google.ai.edge.litertlm.Engine")
    }.isSuccess

    companion object {
        const val MAX_PROMPT_CHARS = 8_000
        private val SUPPORTED_ABIS = setOf("arm64-v8a", "x86_64")
    }
}
