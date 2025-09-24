package com.sqlswitcher

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class SqlSwitcherApplication

fun main(args: Array<String>) {
    runApplication<SqlSwitcherApplication>(*args)
}
