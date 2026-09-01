package fi.ldf.warsampo.linter;

record RepairDefinition(
        String id,
        String localId,
        String ruleId,
        String badIri,
        String replacementIri,
        Profile validationProfile,
        String update) {}
