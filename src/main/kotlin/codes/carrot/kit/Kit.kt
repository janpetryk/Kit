package codes.carrot.kit

object Kit {
    val LOGGER = loggerFor<Kit>()

    @JvmStatic fun main(args: Array<String>) {
        LOGGER.info("hello, kit!")
    }
}