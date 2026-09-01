package fi.ldf.warsampo.linter;

import java.time.Duration;

record UnionRuleTiming(String rule, int focusNodes, Duration duration) {}
