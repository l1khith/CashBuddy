package com.l1khith.cashbuddy

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform