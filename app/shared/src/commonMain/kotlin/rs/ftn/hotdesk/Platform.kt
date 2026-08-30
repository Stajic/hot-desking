package rs.ftn.hotdesk

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform