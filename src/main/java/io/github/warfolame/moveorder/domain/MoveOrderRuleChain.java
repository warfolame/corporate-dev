package io.github.warfolame.moveorder.domain;

import java.util.List;

/**
 * Several rules that act as one: each rule is checked in order,
 * and the first one that forbids the change stops it.
 */
public final class MoveOrderRuleChain implements MoveOrderRule {

    private final List<MoveOrderRule> rules;

    public MoveOrderRuleChain(List<MoveOrderRule> rules) {
        this.rules = List.copyOf(rules);
    }

    @Override
    public void check(MoveOrder order, MoveOrderStatus to) {
        for (MoveOrderRule rule : rules) {
            rule.check(order, to);
        }
    }
}
