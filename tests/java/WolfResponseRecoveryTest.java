package ai.wolf.autopilot;

/** Regression for user screenshot: WOLF did not reply to simple capability question. */
public final class WolfResponseRecoveryTest {
    private static int checks;
    private static void check(boolean ok,String reason) {
        checks++;if(!ok)throw new AssertionError(reason);
    }
    public static void main(String[] args) {
        check(WolfHelp.isCapabilityQuestion("Что ты можешь ?"),"screenshot question");
        check(WolfHelp.isCapabilityQuestion("Что ты можешь"),"plain variation");
        check(WolfHelp.isCapabilityQuestion("Какие у тебя возможности?"),"capabilities");
        check(!WolfHelp.isCapabilityQuestion("Поставь будильник"),"avoid side effects");
        String answer=WolfHelp.capabilities();
        check(answer.contains("🎙️"),"speaking ability");
        check(answer.contains("TikTok Studio"),"direct TikTok support");
        check(answer.contains("ChatGPT Work"),"transparent limitations");
        check(!answer.contains("Нужно загрузить модель"),"no model precondition");
        check(WolfHelp.promptForEmpty().contains("Напиши хотя бы пару слов"),
                "single question mark has useful reply");
        check(WolfHelp.recover("Что ты можешь ?").contains("Что ты можешь ?"),
                "recovery recognizes unanswered query");
        check(WolfHelp.recover(null).contains("предыдущего вопроса"),"no history");
        check(WolfCommandRouter.route("Что ты можешь ?").kind ==
                WolfCommandRouter.Kind.HELP,"natural-language routing");
        check(WolfCommandRouter.route("?").kind ==
                WolfCommandRouter.Kind.SHORT_PROMPT,"one-character guidance");
        check(WolfCommandRouter.route("Ты не ответил на предыдущий вопрос").kind ==
                WolfCommandRouter.Kind.RETRY_PREVIOUS,"safe retry intent");
        System.out.println("PASS "+checks+" guaranteed chat response and retry checks");
    }
}
