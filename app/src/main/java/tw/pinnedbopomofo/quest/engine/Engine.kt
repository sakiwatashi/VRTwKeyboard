package tw.pinnedbopomofo.quest.engine

class Engine(val lexicon: Lexicon, val predictor: Predictor) {
    companion object {
        fun load(open: DataOpener) = Engine(Lexicon.load(open), Predictor.load(open))
    }
}
