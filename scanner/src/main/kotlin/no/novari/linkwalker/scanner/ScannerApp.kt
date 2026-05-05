package no.novari.linkwalker.scanner

import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.context.annotation.ComponentScan
import kotlin.system.exitProcess

@SpringBootApplication
@ComponentScan(basePackages = ["no.novari.linkwalker", "no.novari.metamodel"])
@ConfigurationPropertiesScan(basePackages = ["no.novari.linkwalker"])
class ScannerApp

fun main(args: Array<String>) {
    val context = SpringApplication.run(ScannerApp::class.java, *args)
    exitProcess(SpringApplication.exit(context))
}
