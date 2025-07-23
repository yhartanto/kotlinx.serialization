package kotlinx.benchmarks.json

import benchmarks.model.moshigen.*
import com.squareup.moshi.*
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import benchmarks.model.CitmCatalog
import kotlinx.serialization.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.protobuf.*
import org.openjdk.jmh.annotations.*
import java.util.concurrent.*

@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 7, time = 1)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@State(Scope.Benchmark)
@Fork(2)
@OptIn(ExperimentalSerializationApi::class)
open class CitmBenchmark {
    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private val jsonAdapter = moshi.adapter(CitmCatalog::class.java)

    private val moshiGenAdapter = CitmCatalogJsonAdapter(moshi = moshi)

    /*
     * For some reason Citm is kind of de-facto standard cross-language benchmark.
     * Order of magnitude: 200 ops/sec
     */
    private val input =
        CitmBenchmark::class.java.getResource("/citm_catalog.json").readBytes().decodeToString()
    private val citm = Json.decodeFromString(CitmCatalog.serializer(), input)
    private val citmGen =
        Json.decodeFromString(benchmarks.model.moshigen.CitmCatalog.serializer(), input)

    private val byteArrayInput = ProtoBuf.encodeToByteArray(CitmCatalog.serializer(), citm)

    @Setup
    fun init() {
        require(citm == Json.decodeFromString(CitmCatalog.serializer(), Json.encodeToString(citm)))
        require(
            citmGen == Json.decodeFromString(
                benchmarks.model.moshigen.CitmCatalog.serializer(),
                Json.encodeToString(citm)
            )
        )
    }

    @Benchmark
    fun decodeCitm(): CitmCatalog = Json.decodeFromString(CitmCatalog.serializer(), input)

    @Benchmark
    fun decodeCitmProto(): CitmCatalog =
        ProtoBuf.decodeFromByteArray(CitmCatalog.serializer(), byteArrayInput)

    @Benchmark
    fun decodeCitmMoshi(): CitmCatalog = jsonAdapter.fromJson(input)!!

    @Benchmark
    fun decodeCitmMoshiGen(): benchmarks.model.moshigen.CitmCatalog =
        moshiGenAdapter.fromJson(input)!!

    @Benchmark
    fun encodeCitm(): String = Json.encodeToString(CitmCatalog.serializer(), citm)

    @Benchmark
    fun encodeCitmProto(): ByteArray = ProtoBuf.encodeToByteArray(CitmCatalog.serializer(), citm)

    @Benchmark
    fun encodeCitmMoshi(): String = jsonAdapter.toJson(citm)

    @Benchmark
    fun encodeCitmMoshiGen(): String = moshiGenAdapter.toJson(citmGen)

}
