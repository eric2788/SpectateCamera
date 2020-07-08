class Player(
        val name: String,
        val age: Int
)

fun main() {
    val p1 = Player("Lam", 15)
    val p2 = Player("Lam", 15)
    val p3 = Player("Chan", 14)
    listOf(p1, p2, p3).forEachIndexed { i, p -> println("$p$i -> ${p.hashCode()}") }
}