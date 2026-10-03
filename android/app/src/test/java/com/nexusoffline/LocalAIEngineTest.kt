package com.nexusoffline

import java.io.ByteArrayInputStream
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class LocalAIEngineTest {
    @Rule @JvmField val temporaryFolder = TemporaryFolder()

    @Test fun unavailableRuntimeDoesNotPretendToImportLoadOrGenerate() {
        val engine: LocalAIEngine = UnavailableLocalAIEngine()
        val status = engine.getRuntimeStatus()
        assertFalse(status.runtimeInstalled)
        assertNull(status.runtimeName)
        assertNull(status.loadedModelId)
        assertTrue(status.installedModels.isEmpty())
        assertEquals(LOCAL_AI_MODEL_NOT_INSTALLED, status.message)
        assertEquals(LOCAL_AI_MODEL_NOT_INSTALLED, status.modelStatus)
        assertEquals("NO_LOCAL_AI_RUNTIME", engine.initializeRuntime())
        assertEquals("MODEL_IMPORT_REQUIRES_DOCUMENT_PICKER", engine.importModel("model.bin"))
        assertEquals("NO_LOCAL_AI_RUNTIME", engine.loadModel("model-id"))
        assertEquals("NO_LOCAL_AI_RUNTIME", engine.generate("hello"))
        assertFalse(engine.cancelGeneration())
        assertFalse(engine.unloadModel())
        assertEquals("NO_LOCAL_AI_RUNTIME", engine.deleteModel("model-id"))
    }

    @Test fun noModelHasRequiredExplicitStatus() {
        val status = UnavailableLocalAIEngine().getRuntimeStatus()
        assertTrue(status.installedModels.isEmpty())
        assertEquals("LOCAL AI — MODEL NOT INSTALLED", status.modelStatus)
        assertEquals("LOCAL AI — MODEL NOT INSTALLED", status.message)
    }

    @Test fun modelMetadataCanBeInspectedWithoutStartingInference() {
        val root = File(temporaryFolder.root, "models")
        val store = LocalAIModelStore(root) { Long.MAX_VALUE }
        val model = store.importModel("inspected.litertlm", ByteArrayInputStream(LiteRtLmContainerFixture.bytes()))
        val unavailable = UnavailableLocalAIEngine()
        assertTrue(model.inspection?.structurallyCompatible == true)
        assertEquals("LiteRT-LM container", model.inspection?.format)
        assertEquals("1.0.0", model.inspection?.formatVersion)
        assertNull(unavailable.inspectModel(model.id))
        assertFalse(unavailable.generate("test") == "GENERATION_STARTED")
    }

    @Test fun nativeLoadFailureIsReportedForUnsupportedAbiWithoutClaimingInference() {
        val root = File(temporaryFolder.root, "models")
        val store = LocalAIModelStore(root) { Long.MAX_VALUE }
        val model = store.importModel("structural-fixture.litertlm", ByteArrayInputStream(LiteRtLmContainerFixture.bytes()))
        val engine = LiteRtLocalAIEngine(
            modelStore = store,
            cacheDirectory = File(temporaryFolder.root, "cache"),
            deviceAbis = listOf("armeabi-v7a"),
            availableStorageBytes = { Long.MAX_VALUE },
            availableRamBytes = { Long.MAX_VALUE },
            totalRamBytes = { Long.MAX_VALUE }
        ) { _, _ -> }
        assertFalse(engine.getRuntimeStatus().runtimeInstalled)
        assertEquals("UNSUPPORTED_DEVICE_ABI_OR_RUNTIME", engine.loadModel(model.id))
        assertEquals("MODEL_NOT_LOADED", engine.getRuntimeStatus().modelStatus)
        engine.close()
    }

    @Test fun lowRamPreflightRejectsLoadBeforeNativeInitialization() {
        val root = File(temporaryFolder.root, "models")
        val store = LocalAIModelStore(root) { Long.MAX_VALUE }
        val model = store.importModel("structural-fixture.litertlm", ByteArrayInputStream(LiteRtLmContainerFixture.bytes()))
        val engine = LiteRtLocalAIEngine(
            modelStore = store,
            cacheDirectory = File(temporaryFolder.root, "cache"),
            deviceAbis = listOf("arm64-v8a"),
            availableStorageBytes = { Long.MAX_VALUE },
            availableRamBytes = { 1L },
            totalRamBytes = { 1L }
        ) { _, _ -> }
        assertEquals("MODEL_MEMORY_ESTIMATE_EXCEEDS_AVAILABLE_RAM", engine.loadModel(model.id))
        assertEquals("IDLE", engine.getRuntimeStatus().state)
        assertEquals("LOW_AVAILABLE_RAM_ESTIMATE", engine.getRuntimeStatus().installedModels.single().compatibility)
        engine.close()
    }

    @Test fun cancellationInvalidatesAnOutstandingGenerationCallbackToken() {
        val fence = LocalAIGenerationFence()
        val pendingCallback = fence.nextToken()
        assertTrue(fence.isCurrent(pendingCallback))
        fence.invalidate()
        assertFalse(fence.isCurrent(pendingCallback))
    }

    @Test fun fakeRuntimeCancellationFencesLateCallbacksAndAllowsReloadedGeneration() {
        val store = LocalAIModelStore(File(temporaryFolder.root, "models")) { Long.MAX_VALUE }
        val model = store.importModel("lifecycle.litertlm", ByteArrayInputStream(LiteRtLmContainerFixture.bytes()))
        val factory = FakeRuntimeFactory()
        val chunks = CopyOnWriteArrayList<String>()
        val engine = newFakeRuntimeAdapter(store, factory, chunks)

        assertEquals("MODEL_LOADING", engine.loadModel(model.id))
        awaitState(engine, "READY")
        assertEquals("GENERATION_STARTED", engine.generate("first prompt"))
        awaitCondition("first generation callback") { factory.latestConversation()?.hasCallback == true }
        val cancelledConversation = factory.latestConversation()!!
        assertEquals("GENERATION_BUSY", engine.generate("overlapping prompt"))

        assertTrue(engine.cancelGeneration())
        assertEquals("CANCELLED", engine.getRuntimeStatus().state)
        awaitCondition("cancelled runtime cleanup") { cancelledConversation.closed && factory.latestEngine()?.closed == true }
        assertEquals("MODEL_NOT_LOADED", engine.generate("must reload first"))
        cancelledConversation.complete("stale output")
        assertEquals("CANCELLED", engine.getRuntimeStatus().state)
        assertTrue(chunks.isEmpty())

        assertEquals("MODEL_LOADING", engine.loadModel(model.id))
        awaitState(engine, "READY")
        assertEquals("GENERATION_STARTED", engine.generate("second prompt"))
        awaitCondition("reloaded generation callback") { factory.latestConversation()?.hasCallback == true }
        factory.latestConversation()!!.complete("fresh output")
        awaitState(engine, "READY")
        assertEquals(listOf("fresh output"), chunks.toList())
        assertTrue(engine.unloadModel())
        assertEquals("IDLE", engine.getRuntimeStatus().state)
        assertNull(engine.getRuntimeStatus().loadedModelId)
        engine.close()
    }

    @Test fun loadedModelCannotBeDeletedAndAnotherModelCanBeImportedWithoutReplacingIt() {
        val store = LocalAIModelStore(File(temporaryFolder.root, "models")) { Long.MAX_VALUE }
        val first = store.importModel("first.litertlm", ByteArrayInputStream(LiteRtLmContainerFixture.bytes()))
        val factory = FakeRuntimeFactory()
        val engine = newFakeRuntimeAdapter(store, factory)
        assertEquals("MODEL_LOADING", engine.loadModel(first.id))
        awaitState(engine, "READY")

        assertEquals("MODEL_MUST_BE_UNLOADED", engine.deleteModel(first.id))
        val second = store.importModel("second.litertlm", ByteArrayInputStream(LiteRtLmContainerFixture.bytes(architecture = "future_decoder")))
        assertEquals("MODEL_IMPORTED", engine.registerImportedModel(second))
        assertEquals(2, engine.getRuntimeStatus().installedModels.size)
        assertEquals(first.id, engine.getRuntimeStatus().loadedModelId)

        assertTrue(engine.unloadModel())
        assertEquals("MODEL_DELETED", engine.deleteModel(first.id))
        assertEquals(1, engine.getRuntimeStatus().installedModels.size)
        engine.close()
    }

    @Test fun adapterRecreationStartsUnloadedAndCanLoadPersistedModelAgain() {
        val store = LocalAIModelStore(File(temporaryFolder.root, "models")) { Long.MAX_VALUE }
        val model = store.importModel("persisted.litertlm", ByteArrayInputStream(LiteRtLmContainerFixture.bytes()))
        val firstFactory = FakeRuntimeFactory()
        val firstAdapter = newFakeRuntimeAdapter(store, firstFactory)
        assertEquals("MODEL_LOADING", firstAdapter.loadModel(model.id))
        awaitState(firstAdapter, "READY")
        firstAdapter.close()
        assertEquals("CLOSED", firstAdapter.getRuntimeStatus().state)
        assertNull(firstAdapter.getRuntimeStatus().loadedModelId)
        assertTrue(firstFactory.latestEngine()!!.closed)

        val recreated = newFakeRuntimeAdapter(store, FakeRuntimeFactory())
        assertEquals("IDLE", recreated.getRuntimeStatus().state)
        assertNull(recreated.getRuntimeStatus().loadedModelId)
        assertEquals(1, recreated.getRuntimeStatus().installedModels.size)
        assertEquals("MODEL_LOADING", recreated.loadModel(model.id))
        awaitState(recreated, "READY")
        recreated.close()
    }

    @Test fun unloadDuringGenerationFencesLateOutputAndClosesRuntimeOffThread() {
        val store = LocalAIModelStore(File(temporaryFolder.root, "models")) { Long.MAX_VALUE }
        val model = store.importModel("unload-race.litertlm", ByteArrayInputStream(LiteRtLmContainerFixture.bytes()))
        val factory = FakeRuntimeFactory()
        val chunks = CopyOnWriteArrayList<String>()
        val engine = newFakeRuntimeAdapter(store, factory, chunks)
        assertEquals("MODEL_LOADING", engine.loadModel(model.id))
        awaitState(engine, "READY")
        assertEquals("GENERATION_STARTED", engine.generate("cancel by unload"))
        awaitCondition("active generation callback") { factory.latestConversation()?.hasCallback == true }
        val activeConversation = factory.latestConversation()!!
        val activeEngine = factory.latestEngine()!!

        assertTrue(engine.unloadModel())
        assertEquals("IDLE", engine.getRuntimeStatus().state)
        activeConversation.complete("late output")
        awaitCondition("unloaded runtime cleanup") { activeConversation.closed && activeEngine.closed }
        assertEquals("IDLE", engine.getRuntimeStatus().state)
        assertTrue(chunks.isEmpty())
        engine.close()
    }

    @Test fun adapterCloseDuringGenerationFencesCallbacksForActivityTeardown() {
        val store = LocalAIModelStore(File(temporaryFolder.root, "models")) { Long.MAX_VALUE }
        val model = store.importModel("close-race.litertlm", ByteArrayInputStream(LiteRtLmContainerFixture.bytes()))
        val factory = FakeRuntimeFactory()
        val chunks = CopyOnWriteArrayList<String>()
        val engine = newFakeRuntimeAdapter(store, factory, chunks)
        assertEquals("MODEL_LOADING", engine.loadModel(model.id))
        awaitState(engine, "READY")
        assertEquals("GENERATION_STARTED", engine.generate("close during generation"))
        awaitCondition("active generation callback") { factory.latestConversation()?.hasCallback == true }
        val activeConversation = factory.latestConversation()!!
        val activeEngine = factory.latestEngine()!!

        engine.close()
        assertEquals("CLOSED", engine.getRuntimeStatus().state)
        activeConversation.complete("late output")
        awaitCondition("closed runtime cleanup") { activeConversation.closed && activeEngine.closed }
        assertEquals("CLOSED", engine.getRuntimeStatus().state)
        assertTrue(chunks.isEmpty())
    }

    private fun newFakeRuntimeAdapter(
        store: LocalAIModelStore,
        factory: FakeRuntimeFactory,
        chunks: CopyOnWriteArrayList<String> = CopyOnWriteArrayList()
    ) = LiteRtLocalAIEngine(
        modelStore = store,
        cacheDirectory = File(temporaryFolder.root, "cache"),
        deviceAbis = listOf("arm64-v8a"),
        availableStorageBytes = { Long.MAX_VALUE },
        availableRamBytes = { Long.MAX_VALUE },
        totalRamBytes = { Long.MAX_VALUE },
        runtimeFactory = factory
    ) { method, payload ->
        if (method == "localAIGenerationChunk") chunks += payload.getString("text")
    }

    private fun awaitState(engine: LocalAIEngine, expected: String) =
        awaitCondition("state $expected") { engine.getRuntimeStatus().state == expected }

    private fun awaitCondition(description: String, condition: () -> Boolean) {
        val deadline = System.nanoTime() + 3_000_000_000L
        while (!condition() && System.nanoTime() < deadline) Thread.sleep(10)
        assertTrue("Timed out waiting for $description", condition())
    }
}

private class FakeRuntimeFactory : LocalAIRuntimeFactory {
    private val engines = CopyOnWriteArrayList<FakeRuntimeEngine>()
    override fun create(modelPath: String, cacheDirectory: String): LocalAIRuntimeEngine =
        FakeRuntimeEngine().also { engines += it }
    fun latestEngine(): FakeRuntimeEngine? = engines.lastOrNull()
    fun latestConversation(): FakeRuntimeConversation? = latestEngine()?.conversations?.lastOrNull()
}

private class FakeRuntimeEngine : LocalAIRuntimeEngine {
    @Volatile var closed = false
    val conversations = CopyOnWriteArrayList<FakeRuntimeConversation>()
    override fun initialize() = Unit
    override fun createConversation(): LocalAIRuntimeConversation = FakeRuntimeConversation().also { conversations += it }
    override fun close() { closed = true }
}

private class FakeRuntimeConversation : LocalAIRuntimeConversation {
    @Volatile private var callback: LocalAIRuntimeMessageCallback? = null
    @Volatile var closed = false
    val hasCallback: Boolean get() = callback != null
    override fun sendMessageAsync(prompt: String, callback: LocalAIRuntimeMessageCallback) { this.callback = callback }
    fun complete(text: String) {
        callback?.onMessage(text)
        callback?.onDone()
    }
    override fun close() { closed = true }
}
