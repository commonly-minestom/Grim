package ac.grim.grimac.platform.minestom;

import net.minestom.server.Auth;

class OfflineModeIntegrationTest extends GrimMinestomIntegrationTest {
    OfflineModeIntegrationTest() {
        super(new Auth.Offline());
    }
}
