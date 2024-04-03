object Util {
    fun getRandomString(chars: String, length: Int): String {
        val stringBuilder = StringBuilder()

        for (i in 0 until length) {
            stringBuilder.append(chars[(chars.length * Math.random()).toInt()])
        }

        return stringBuilder.toString()
    }
}