import startup.*


fun main(args: Array<String>) {
    val cli = CliParser.parse(args) ?: return
    OptDriver(cli).compile()
}