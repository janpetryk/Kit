package codes.carrot.kit

import com.codahale.metrics.annotation.Timed
import com.ibm.icu.text.BreakIterator
import redis.clients.jedis.JedisPool
import redis.clients.jedis.exceptions.JedisConnectionException
import java.security.SecureRandom
import javax.ws.rs.*
import javax.ws.rs.core.MediaType
import javax.ws.rs.core.Response

interface ILinkDataSource {
    fun get(id: String): String?
}

class LinkDataSource(private val jedisPool: JedisPool) : ILinkDataSource {
    override fun get(id: String): String? {
        return try {
            jedisPool.resource.use {
                return it.get("link.id.$id")
            }
        } catch (exception: JedisConnectionException) {
            null
        }
    }
}

interface ILinkDataSink {
    fun store(id: String, link: String): String?
}

interface ILinkHashingStrategy {
    fun hash(link: String): String
}

class LinkDataSink(private val jedisPool: JedisPool, private val hashingStrategy: ILinkHashingStrategy) : ILinkDataSink {

    override fun store(id: String, link: String): String? {
        return try {
            val hashedLink = hashingStrategy.hash(link)

            jedisPool.resource.use {
                val existingLinkId = it.get("link.hash.$hashedLink")
                if (existingLinkId != null) {
                    existingLinkId
                } else {
                    val idStatusCode = it.set("link.id.$id", link)
                    val hashStatusCode = it.set("link.hash.$hashedLink", id)
                    // todo: desync
                    if (idStatusCode == "OK" && hashStatusCode == "OK") {
                        id
                    } else {
                        null
                    }
                }
            }
        } catch (exception: JedisConnectionException) {
            null
        }
    }

}

interface IIdGenerator {
    fun next(): String
}

fun extractGraphemeClusters(input: String): List<String> {
    val characters = mutableListOf<String>()

    val iterator = BreakIterator.getCharacterInstance()
    iterator.setText(input)

    var start = iterator.first()
    var iterated = false
    while (!iterated) {
        val next = iterator.next()
        if (next == BreakIterator.DONE) {
            iterated = true
            continue
        }

        val extracted = input.substring(start, next)

        start = next

        characters += extracted
    }

    return characters
}

class GraphemeClusterIdGenerator(private val length: Int, inputString: String): IIdGenerator {

    private val characters = extractGraphemeClusters(inputString)

    private var rnd = SecureRandom()

    override fun next(): String {
        val sb = StringBuilder(length)

        for (i in 0..length - 1) {
            sb.append(characters[rnd.nextInt(characters.size)])
        }

        return sb.toString()
    }

}

@Path("/")
@Produces(MediaType.APPLICATION_JSON + ";charset=utf-8")
class LinkShortenService(private val linkDataSource: ILinkDataSource, private val linkDataSink: ILinkDataSink, private val idGenerator: IIdGenerator) {

    private val LOGGER = loggerFor<LinkShortenService>()
    private val LINK_MAX = 2083

    @Path("{id}") @GET @Timed fun get(@PathParam("id") id: String): Response {
        if (!isValidId(id)) {
            return constructBadRequestResponse("id must be [1..10] long")
        }

        val link = linkDataSource.get(id) ?: return idNotFoundResponse(id)

        return constructLinkResponse(link)
    }

    @Path("/link") @POST @Timed fun put(request: PostRequest): Response {
        val link = request.link

        if (!isValidLink(link)) {
            LOGGER.info("invalid link")
            return constructBadRequestResponse("link must be [1..$LINK_MAX] long, and start with http:// or https://")
        }

        val id = if (request.id != null) {
            if (request.id.isBlank()) {
                return@put constructBadRequestResponse("id for storage must be [1..10] long")
            }

            request.id
        } else {
            idGenerator.next()
        }

        val existingLink = linkDataSource.get(id)
        if (existingLink != null) {
            LOGGER.info("link with id already exists, bailing out")
            return constructServerFailureResponse("something already exists at that id")
        }

        LOGGER.info("storing id and link: $id -> $link")
        val storedId = linkDataSink.store(id, link)
        if (storedId == null) {
            LOGGER.info("failed to store id and link using sink")
            return constructServerFailureResponse("failed to store id and link")
        }

        return constructLinkStoredResponse(storedId, link)
    }

    private fun isValidId(id: String): Boolean {
        val graphemeClusters = extractGraphemeClusters(id)

        if (id.isNullOrEmpty() || graphemeClusters.size > 10) {
            return false
        }

        return true
    }

    private fun isValidLink(link: String?): Boolean {
        if (link == null) {
            return false
        }

        val httpPrefix = "http://"

        if (link.length < httpPrefix.length || link.length > LINK_MAX) {
            return false
        }

        if (!link.startsWith("http://") && !link.startsWith("https://")) {
            return false
        }

        return true

    }

    data class GetResponse(val link: String)
    data class PostRequest(val link: String, val id: String?)
    data class PostResponse(val id: String, val link: String)

    private fun constructLinkResponse(link: String): Response {
        return Response.status(Response.Status.TEMPORARY_REDIRECT).entity(GetResponse(link)).build()
    }

    private fun constructLinkStoredResponse(id: String, link: String): Response {
        return Response.status(Response.Status.OK).entity(PostResponse(id, link)).build()
    }

    private fun constructServerFailureResponse(message: String): Response {
        return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(
                ErrorResponseV1(Response.Status.INTERNAL_SERVER_ERROR.statusCode.toString(), message)).build()
    }

    private fun constructBadRequestResponse(message: String): Response {
        return Response.status(Response.Status.BAD_REQUEST).entity(
                ErrorResponseV1(Response.Status.BAD_REQUEST.statusCode.toString(), message)).build()
    }

    private fun idNotFoundResponse(id: String): Response {
        return Response.status(Response.Status.NOT_FOUND).entity(
                ErrorResponseV1(Response.Status.NOT_FOUND.statusCode.toString(), id)).build()
    }

}