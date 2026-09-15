package tw.pinnedbopomofo.quest

/** 表情符號頁的分類。只收常用、Android 14（Quest 的系統）內建字型畫得出來的表情。 */
object EmojiCatalog {
    class Category(val icon: String, val emojis: List<String>)

    private fun list(text: String) = text.trim().split(Regex("\\s+"))

    val categories = listOf(
        Category("😀", list("""
            😀 😃 😄 😁 😆 😅 🤣 😂 🙂 😉 😊 😇 🥰 😍 🤩 😘 😋 😛 😜 🤪
            😝 🤑 🤗 🤭 🤫 🤔 🤐 🤨 😐 😑 😶 😏 😒 🙄 😬 😌 😔 😪 😴 😷
            🤒 🤕 🤢 🤮 🥵 🥶 😵 🤯 🥳 😎 🤓 🧐 😕 😟 🙁 😮 😯 😲 😳 🥺
            😦 😧 😨 😰 😥 😢 😭 😱 😖 😣 😞 😓 😩 😫 🥱 😤 😡 😠 🤬 😈
            👿 💀 💩 🤡 👻 👽 🤖
        """)),
        Category("👋", list("""
            👋 🤚 🖐️ ✋ 🖖 👌 🤌 🤏 ✌️ 🤞 🤟 🤘 🤙 👈 👉 👆 👇 ☝️ 👍 👎
            ✊ 👊 🤛 🤜 👏 🙌 👐 🤲 🤝 🙏 💪 👀 👶 🧒 👦 👧 🧑 👨 👩 🧓
            👴 👵 🙋 🙆 🙅 🤷 🤦 💁 🙇
        """)),
        Category("🐶", list("""
            🐶 🐱 🐭 🐹 🐰 🦊 🐻 🐼 🐨 🐯 🦁 🐮 🐷 🐸 🐵 🙈 🙉 🙊 🐔 🐧
            🐦 🐤 🦆 🦉 🐺 🐗 🐴 🦄 🐝 🦋 🐌 🐞 🐢 🐍 🐙 🦀 🐠 🐬 🐳 🌸
            🌹 🌻 🌷 🌱 🌲 🌳 🍀 🍁 🍂 🌈 ☀️ 🌙 ⭐ ⚡ 🔥 ❄️ ☔ 🌊
        """)),
        Category("🍔", list("""
            🍎 🍊 🍋 🍌 🍉 🍇 🍓 🍒 🍑 🥭 🍍 🥝 🍅 🥑 🌽 🥕 🍞 🧀 🍳 🥓
            🍔 🍟 🍕 🌭 🥪 🌮 🍜 🍝 🍣 🍱 🍛 🍚 🍙 🥟 🍤 🍦 🍰 🎂 🍫 🍬
            🍩 🍪 🧋 ☕ 🍵 🍺 🍻 🥂 🍷 🥤
        """)),
        Category("⚽", list("""
            ⚽ 🏀 🏈 ⚾ 🎾 🏐 🏓 🏸 🥊 🎯 🎮 🕹️ 🎲 🧩 🎨 🎬 🎤 🎧 🎵 🎶
            🎹 🎸 🥁 🏆 🥇 🎉 🎊 🎁 🎈 🎄 🧧 🏃 🚴 🏊 🧘
        """)),
        Category("✈️", list("""
            🚗 🚕 🚌 🚓 🚑 🚒 🏍️ 🚲 🚂 🚄 🚇 ✈️ 🚀 🛸 🚢 ⛵ 🗺️ 🏠 🏢 🏫
            🏥 🏪 🏯 🗼 🗽 ⛩️ 🌋 🏖️ 🏝️ 🌃 🌆 🎡 🎢
        """)),
        Category("💡", list("""
            📱 💻 ⌨️ 🖥️ 📷 📺 ⏰ ⌛ 💡 🔋 🔌 💰 💳 🎫 📦 📫 ✏️ 📝 📚 📌
            📎 ✂️ 🔒 🔑 🔨 🧰 💊 🩹 🧸 🛒 🎒 👓 👕 👖 👗 👟 👑 💍
        """)),
        Category("❤️", list("""
            ❤️ 🧡 💛 💚 💙 💜 🖤 🤍 🤎 💔 💕 💞 💓 💗 💖 💘 💝 💯 💢 💥
            💫 💦 💨 💬 💭 ✅ ❌ ❓ ❗ ⭕ 🚫 ⚠️ 🆗 🆕 🆒 ➕ ➖ ✖️ ➗ ♻️
            🔴 🟢 🔵
        """)),
    )
}
