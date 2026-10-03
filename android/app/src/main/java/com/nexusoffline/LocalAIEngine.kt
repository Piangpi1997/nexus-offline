package com.nexusoffline

import java.util.concurrent.atomic.AtomicLong

internal const val LOCAL_AI_MODEL_NOT_INSTALLED = "LOCAL AI — MODEL NOT INSTALLED"

internal data class LocalAIModelInfo(
    val id: String,
    val displayName: String,
    val license: String?,
    val requiredRamBytes: Long?,
    val requiredStorageBytes: Long?,
    val availableRamBytes: Long?,
    val format: String,
    val formatVersion: String?,
    val architecture: String?,
    val contextLength: Int?,
    val compatibility: String,
    val supportedAbis: List<String>
)

internal data class LocalAIStatus(
    val runtimeInstalled: Boolean,
    val runtimeName: String?,
    val loadedModelId: String?,
    val installedModels: List<LocalAIModelInfo>,
    val message: String,
    val state: String = "UNAVAILABLE",
    val errorCode: String? = null,
    val supportedAbis: List<String> = emptyList(),
    val availableStorageBytes: Long? = null,
    val availableRamBytes: Long? = null,
    val totalRamBytes: Long? = null,
    val modelStatus: String = LOCAL_AI_MODEL_NOT_INSTALLED
)

/** On-device-only inference contract. Implementations must never use a network/cloud fallback. */
internal interface LocalAIEngine {
    fun initializeRuntime(): String
    fun getRuntimeStatus(): LocalAIStatus
    fun inspectModel(modelId: String): LocalAIModelInfo?
    /** Starts the Android Storage Access Framework picker; this is not a download operation. */
    fun importModel(sourceDescription: String): String
    fun registerImportedModel(model: ImportedLocalAIModel): String
    fun loadModel(modelId: String): String
    /** Starts asynchronous local generation and returns immediately with a stable result code. */
    fun generate(prompt: String): String
    fun cancelGeneration(): Boolean
    fun unloadModel(): Boolean
    fun deleteModel(modelId: String): String
    fun close()
}

/** Invalidates callbacks from cancelled, unloaded or superseded local operations. */
internal class LocalAIGenerationFence {
    private val sequence = AtomicLong(0L)

    fun nextToken(): Long = sequence.incrementAndGet()
    fun invalidate(): Long = sequence.incrementAndGet()
    fun isCurrent(token: Long): Boolean = sequence.get() == token
}

/** Used only when runtime/ABI is unavailable; it never returns fabricated output. */
internal class UnavailableLocalAIEngine : LocalAIEngine {
    override fun initializeRuntime() = "NO_LOCAL_AI_RUNTIME"

    override fun getRuntimeStatus() = LocalAIStatus(
        runtimeInstalled = false,
        runtimeName = null,
        loadedModelId = null,
        installedModels = emptyList(),
        message = LOCAL_AI_MODEL_NOT_INSTALLED,
        state = "UNAVAILABLE",
        errorCode = "NO_LOCAL_AI_RUNTIME",
        modelStatus = LOCAL_AI_MODEL_NOT_INSTALLED
    )

    override fun inspectModel(modelId: String): LocalAIModelInfo? = null
    override fun importModel(sourceDescription: String) = "MODEL_IMPORT_REQUIRES_DOCUMENT_PICKER"
    override fun registerImportedModel(model: ImportedLocalAIModel) = "NO_LOCAL_AI_RUNTIME"
    override fun loadModel(modelId: String) = "NO_LOCAL_AI_RUNTIME"
    override fun generate(prompt: String) = "NO_LOCAL_AI_RUNTIME"
    override fun cancelGeneration() = false
    override fun unloadModel() = false
    override fun deleteModel(modelId: String) = "NO_LOCAL_AI_RUNTIME"
    override fun close() = Unit
}
