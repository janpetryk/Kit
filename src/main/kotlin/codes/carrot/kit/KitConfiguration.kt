package codes.carrot.kit

import com.bendb.dropwizard.redis.JedisFactory
import com.fasterxml.jackson.annotation.JsonProperty
import io.dropwizard.Configuration
import io.dropwizard.validation.SizeRange
import org.hibernate.validator.constraints.NotEmpty
import javax.validation.constraints.Max
import javax.validation.constraints.Min

class KitConfiguration : Configuration() {
    @NotEmpty
    lateinit var apiKeys: List<String>

    @NotEmpty
    lateinit var urlStart: String

    @Min(1)
    @Max(255)
    lateinit var length: Integer

    @JsonProperty
    val jedis = JedisFactory()
}
