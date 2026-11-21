package org.mhatta.rakkyo;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class BootstrapTest {
    @Test
    public void steps() {
        assertEquals(Bootstrap.Step.CONNECT_DIRECTORY, Bootstrap.currentStep(0));
        assertEquals(Bootstrap.Step.CONNECT_DIRECTORY, Bootstrap.currentStep(15));
        assertEquals(Bootstrap.Step.CONSENSUS, Bootstrap.currentStep(20));
        assertEquals(Bootstrap.Step.CONSENSUS, Bootstrap.currentStep(40));
        assertEquals(Bootstrap.Step.DESCRIPTORS, Bootstrap.currentStep(45));
        assertEquals(Bootstrap.Step.DESCRIPTORS, Bootstrap.currentStep(50));
        assertEquals(Bootstrap.Step.CIRCUIT, Bootstrap.currentStep(80));
        assertEquals(Bootstrap.Step.CIRCUIT, Bootstrap.currentStep(90));
    }

    @Test
    public void stepStates() {
        // The mock-up's 50%: two done, relay information loading, circuit pending
        assertEquals(Bootstrap.StepState.DONE, Bootstrap.stateOf(Bootstrap.Step.CONNECT_DIRECTORY, 50));
        assertEquals(Bootstrap.StepState.DONE, Bootstrap.stateOf(Bootstrap.Step.CONSENSUS, 50));
        assertEquals(Bootstrap.StepState.ACTIVE, Bootstrap.stateOf(Bootstrap.Step.DESCRIPTORS, 50));
        assertEquals(Bootstrap.StepState.PENDING, Bootstrap.stateOf(Bootstrap.Step.CIRCUIT, 50));
        assertEquals(Bootstrap.StepState.DONE, Bootstrap.stateOf(Bootstrap.Step.CIRCUIT, 100));
    }

    @Test
    public void phasesFollowTorsPercentages() {
        // com.subgraph.orchid.Tor.BOOTSTRAP_STATUS_*
        assertEquals(Bootstrap.Phase.STARTING, Bootstrap.phase(0));
        assertEquals(Bootstrap.Phase.CONNECTING_DIRECTORY, Bootstrap.phase(5));
        assertEquals(Bootstrap.Phase.ENCRYPTING_DIRECTORY, Bootstrap.phase(10));
        assertEquals(Bootstrap.Phase.ENCRYPTING_DIRECTORY, Bootstrap.phase(15));
        assertEquals(Bootstrap.Phase.REQUESTING_CONSENSUS, Bootstrap.phase(20));
        assertEquals(Bootstrap.Phase.LOADING_CONSENSUS, Bootstrap.phase(25));
        assertEquals(Bootstrap.Phase.LOADING_KEYS, Bootstrap.phase(35));
        assertEquals(Bootstrap.Phase.LOADING_KEYS, Bootstrap.phase(40));
        assertEquals(Bootstrap.Phase.REQUESTING_DESCRIPTORS, Bootstrap.phase(45));
        assertEquals(Bootstrap.Phase.LOADING_DESCRIPTORS, Bootstrap.phase(50));
        assertEquals(Bootstrap.Phase.CONNECTING_RELAY, Bootstrap.phase(80));
        assertEquals(Bootstrap.Phase.CONNECTING_RELAY, Bootstrap.phase(85));
        assertEquals(Bootstrap.Phase.BUILDING_CIRCUIT, Bootstrap.phase(90));
        assertEquals(Bootstrap.Phase.DONE, Bootstrap.phase(100));
    }
}
