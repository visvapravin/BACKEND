package com.forumx.platform.bootstrap;
import lombok.Getter; import lombok.Setter; import org.springframework.boot.context.properties.ConfigurationProperties;
@Getter @Setter @ConfigurationProperties(prefix="forumx.bootstrap.platform-admin")
public class PlatformAdminBootstrapProperties { private boolean enabled; private String email; private String username; private String password; }
