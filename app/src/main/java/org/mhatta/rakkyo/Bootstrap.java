package org.mhatta.rakkyo;

/**
 * Turns Orchid's bootstrap percentage (Tor's bootstrap phases, see
 * com.subgraph.orchid.Tor.BOOTSTRAP_STATUS_*) into the four steps that the
 * "Connecting" screen shows, and into the message for the current phase.
 *
 * <p>Orchid's own bootstrap messages are English and not meant for users;
 * the app shows its own, translated ones.  Pure Java for the JVM tests.
 */
final class Bootstrap {
    /** The steps shown to the user, in order. */
    enum Step {
        /** 0-15: connecting to a directory server. */
        CONNECT_DIRECTORY,
        /** 20-40: the consensus and the authorities' key certificates. */
        CONSENSUS,
        /** 45-50: relay descriptors. */
        DESCRIPTORS,
        /** 80-99: the first circuit. */
        CIRCUIT
    }

    enum StepState { DONE, ACTIVE, PENDING }

    /** A phase message, for res/values/strings.xml. */
    enum Phase {
        STARTING, CONNECTING_DIRECTORY, ENCRYPTING_DIRECTORY, REQUESTING_CONSENSUS, LOADING_CONSENSUS,
        LOADING_KEYS, REQUESTING_DESCRIPTORS, LOADING_DESCRIPTORS, CONNECTING_RELAY, BUILDING_CIRCUIT, DONE
    }

    private Bootstrap() {
    }

    static Step currentStep(int percent) {
        if (percent < 20) {
            return Step.CONNECT_DIRECTORY;
        } else if (percent < 45) {
            return Step.CONSENSUS;
        } else if (percent < 80) {
            return Step.DESCRIPTORS;
        }
        return Step.CIRCUIT;
    }

    static StepState stateOf(Step step, int percent) {
        if (percent >= 100) {
            return StepState.DONE;
        }
        final int current = currentStep(percent).ordinal();
        if (step.ordinal() < current) {
            return StepState.DONE;
        }
        return step.ordinal() == current ? StepState.ACTIVE : StepState.PENDING;
    }

    static Phase phase(int percent) {
        if (percent >= 100) {
            return Phase.DONE;
        } else if (percent >= 90) {
            return Phase.BUILDING_CIRCUIT;
        } else if (percent >= 80) {
            return Phase.CONNECTING_RELAY;
        } else if (percent >= 50) {
            return Phase.LOADING_DESCRIPTORS;
        } else if (percent >= 45) {
            return Phase.REQUESTING_DESCRIPTORS;
        } else if (percent >= 35) {
            return Phase.LOADING_KEYS;
        } else if (percent >= 25) {
            return Phase.LOADING_CONSENSUS;
        } else if (percent >= 20) {
            return Phase.REQUESTING_CONSENSUS;
        } else if (percent >= 10) {
            return Phase.ENCRYPTING_DIRECTORY;
        } else if (percent >= 5) {
            return Phase.CONNECTING_DIRECTORY;
        }
        return Phase.STARTING;
    }
}
