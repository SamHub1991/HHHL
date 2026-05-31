package cc.hhhl.client.media

fun interface SpeechTextInput {
    fun requestText(
        prompt: String,
        onResult: (String) -> Unit,
        onError: (String) -> Unit,
    )
}
