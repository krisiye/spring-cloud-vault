/*
 * Copyright 2016-2020 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.cloud.vault.config.aws;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.vault.config.LeasingSecretBackendMetadata;
import org.springframework.cloud.vault.config.PropertyNameTransformer;
import org.springframework.cloud.vault.config.SecretBackendMetadata;
import org.springframework.cloud.vault.config.SecretBackendMetadataFactory;
import org.springframework.cloud.vault.config.VaultSecretBackendDescriptor;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.Assert;

/**
 * Bootstrap configuration providing support for the AWS secret backend and STS
 * assumed_role.
 *
 * @author Kris Iyer
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(VaultAwsProperties.class)
@ConditionalOnProperty(name = "spring.cloud.vault.aws.credentialType", havingValue = "assumed_role_test")
public class VaultConfigAwsSTSAssumedRoleBootstrapConfiguration {

	@Bean
	@ConditionalOnMissingBean
	public AwsSecretBackendMetadataFactory awsSecretBackendMetadataFactory(ApplicationContext context) {
		return new AwsSecretBackendMetadataFactory(context);
	}

	/**
	 * {@link SecretBackendMetadataFactory} for AWS integration using
	 * {@link VaultAwsProperties}.
	 */
	public static class AwsSecretBackendMetadataFactory implements SecretBackendMetadataFactory<VaultAwsProperties> {

		private final ApplicationEventPublisher publisher;

		public AwsSecretBackendMetadataFactory() {
			this.publisher = event -> {
			}; // NO-OP;
		}

		public AwsSecretBackendMetadataFactory(ApplicationContext publisher) {
			this.publisher = publisher;
		}

		public ApplicationEventPublisher getPublisher() {
			return this.publisher;
		}

		/**
		 * Creates {@link SecretBackendMetadata} for a secret backend using
		 * {@link VaultAwsProperties}. This accessor transforms Vault's username/password
		 * property names to names provided with
		 * {@link VaultAwsProperties#getAccessKeyProperty()} and
		 * {@link VaultAwsProperties#getSecretKeyProperty()}.
		 * @param properties must not be {@literal null}.
		 * @return the {@link SecretBackendMetadata}
		 */
		LeasingSecretBackendMetadata forAws(final VaultAwsProperties properties) {

			Assert.notNull(properties, "VaultAwsProperties must not be null");
			Assert.isTrue(properties.getCredentialType().equalsIgnoreCase(AWSCredentialType.assumed_role.name()),
					"VaultAwsProperties credentialType should be set to assumed_role");

			PropertyNameTransformer transformer = new PropertyNameTransformer();
			transformer.addKeyTransformation("access_key", properties.getAccessKeyProperty());
			transformer.addKeyTransformation("secret_key", properties.getSecretKeyProperty());
			transformer.addKeyTransformation("security_token", properties.getSessionTokenKeyProperty());

			return new AwsBackendSTSAssumedRoleMetadata(properties, transformer, this.publisher);
		}

		@Override
		public SecretBackendMetadata createMetadata(VaultAwsProperties backendDescriptor) {
			return forAws(backendDescriptor);
		}

		@Override
		public boolean supports(VaultSecretBackendDescriptor backendDescriptor) {
			return backendDescriptor instanceof VaultAwsProperties;
		}

	}

}
