package tw.pinnedbopomofo.quest.engine

class Engine(val lexicon: Lexicon, val predictor: Predictor) {
    companion object {
        fun load(open: DataOpener, timer: LoadTimer? = null) = Engine(Lexicon.load(open, timer), Predictor.load(open, timer))
    }
}
