package ac.grim.grimac.platform.minestom;

import net.minestom.server.Auth;

class OnlineModeIntegrationTest extends GrimMinestomIntegrationTest {
    OnlineModeIntegrationTest() {
        super(new Auth.Online());
    }
}
