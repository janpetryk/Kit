package codes.carrot.kit

import com.bendb.dropwizard.redis.JedisBundle
import com.bendb.dropwizard.redis.JedisFactory
import com.fasterxml.jackson.module.kotlin.KotlinModule
import io.dropwizard.Application
import io.dropwizard.auth.AuthDynamicFeature
import io.dropwizard.auth.Authenticator
import io.dropwizard.auth.basic.BasicCredentials
import io.dropwizard.setup.Bootstrap
import io.dropwizard.setup.Environment
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.security.MessageDigest
import java.security.Security
import kotlin.text.Charsets.UTF_8

val generatedCharacters = "🐶🐱🐭🐹🐰🦊🐻🐼🐨🐯🦁🐮🐷🐸🐵🐔🐧🐦🐤🦉🐺🐗🐴🦄🐝🦋"
val permittedCharacters = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz-_🐶🐱🐭🐹🐰🦊🐻🐼🐨🐯🦁🐮🐷🐸🐵🐔🐧🐦🐤🦉🐺🐗🐴🦄🐝🦋🥕💻✨⚡️⭐️🔥"
val permittedGraphemeClusters = extractGraphemeClusters(permittedCharacters).toSet()

class KitApplication : Application<KitConfiguration>() {
    private val LOGGER = loggerFor<KitApplication>()

    override fun getName() = "Kit"

    override fun initialize(bootstrap: Bootstrap<KitConfiguration>) {
        bootstrap.addBundle(object : JedisBundle<KitConfiguration>() {
            override fun getJedisFactory(configuration: KitConfiguration): JedisFactory {
                return configuration.jedis
            }
        })

        bootstrap.objectMapper.registerModule(KotlinModule())
    }

    @Throws(Exception::class)
    override fun run(configuration: KitConfiguration, environment: Environment) {
        val generatedGraphemeClusters = extractGraphemeClusters(generatedCharacters)

        LOGGER.info("Producing IDs of length ${configuration.length}, containing: $generatedGraphemeClusters")
        LOGGER.info(" and permitting: $permittedGraphemeClusters")

        val jedisPool = configuration.jedis.build(environment)

        Security.addProvider(BouncyCastleProvider())
        val sha3 = MessageDigest.getInstance("SHA3-224") ?: throw RuntimeException("failed to load sha3-224, bailing out")

        val sha3HashingStrategy = object : ILinkHashingStrategy {

            override fun hash(link: String): String {
                return String(sha3.digest(link.toByteArray(charset = UTF_8)))
            }

        }

        environment.applicationContext.errorHandler = JsonErrorHandler()

        val dataSource = LinkDataSource(jedisPool)
        val dataSink = LinkDataSink(jedisPool, sha3HashingStrategy)
        val generator = GraphemeClusterIdGenerator(configuration.length.toInt(), generatedCharacters)

        val linkShortenGetResource = LinkShortenService.GetResource(dataSource)
        val linkShortenPostResource = LinkShortenService.PostResource(dataSource, dataSink, generator, configuration.apiKeys.toSet(), configuration.urlStart)

        environment.jersey().register(linkShortenGetResource)
        environment.jersey().register(linkShortenPostResource)
    }
}