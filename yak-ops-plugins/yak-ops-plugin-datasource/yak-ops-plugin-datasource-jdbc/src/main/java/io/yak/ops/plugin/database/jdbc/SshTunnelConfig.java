package io.yak.ops.plugin.database.jdbc;

import io.yak.ops.plugin.database.jdbc.enums.SshAuthType;

/**
 * Carries optional SSH tunnel settings for a JDBC datasource provider.
 *
 * @param enabled whether SSH tunneling is enabled
 * @param host SSH server host
 * @param port SSH server port
 * @param username SSH login username
 * @param authType authentication method
 * @param password credential for PASSWORD authentication
 * @param privateKey private-key contents for PRIVATE_KEY authentication
 * @param passphrase private-key passphrase
 * @param strictHostKeyChecking whether server host keys are strictly verified
 * @param knownHosts known_hosts contents used for strict verification
 */
public record SshTunnelConfig(
        boolean enabled,
        String host,
        int port,
        String username,
        SshAuthType authType,
        String password,
        String privateKey,
        String passphrase,
        boolean strictHostKeyChecking,
        String knownHosts) {

    /** @return the default configuration with SSH tunneling disabled */
    public static SshTunnelConfig disabled() {
        return new SshTunnelConfig(false, null, 22, null, SshAuthType.PASSWORD, null, null, null, false, null);
    }
}
