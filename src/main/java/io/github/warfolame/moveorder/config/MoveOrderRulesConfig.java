package io.github.warfolame.moveorder.config;

import io.github.warfolame.moveorder.domain.DepositRequiredRule;
import io.github.warfolame.moveorder.domain.MoveOrderPolicy;
import io.github.warfolame.moveorder.domain.MoveOrderRule;
import io.github.warfolame.moveorder.domain.MoveOrderRuleChain;
import io.github.warfolame.moveorder.domain.TransitionRule;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Turns the plain-Java domain rules into Spring beans.
 * The domain classes have no Spring annotations; they are created here with {@code new}.
 */
@Configuration
public class MoveOrderRulesConfig {

    @Bean
    public MoveOrderPolicy moveOrderPolicy() {
        return new MoveOrderPolicy();
    }

    /** The one {@link MoveOrderRule} Spring injects: status table first, then the deposit stop-factor. */
    @Bean
    public MoveOrderRule moveOrderRule(MoveOrderPolicy policy) {
        return new MoveOrderRuleChain(List.of(
                new TransitionRule(policy),
                new DepositRequiredRule()));
    }
}
