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

import java.util.HashMap;
import java.util.Map;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.vault.config.LeasingSecretBackendMetadata;
import org.springframework.cloud.vault.config.PropertyNameTransformer;
import org.springframework.cloud.vault.config.SecretBackendMetadata;
import org.springframework.cloud.vault.config.SecretBackendMetadataFactory;
import org.springframework.cloud.vault.config.VaultSecretBackendDescriptor;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.Assert;
import org.springframework.vault.core.lease.domain.RequestedSecret;
import org.springframework.vault.core.lease.domain.RequestedSecret.Mode;
import org.springframework.vault.core.util.PropertyTransformer;

/**
 * Bootstrap configuration providing support for the AWS secret backend.
 *
 * @author Mark Paluch
 * @author Kris Iyer
 *
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(VaultAwsProperties.class)
public class VaultConfigAwsBootstrapConfiguration {

	@Bean
	@ConditionalOnProperty(name = "spring.cloud.vault.aws.credential-type", havingValue = "iam_user",
			matchIfMissing = true)
	public AwsSecretBackendMetadataFactory awsSecretBackendMetadataFactory() {
		return new AwsSecretBackendMetadataFactory();
	}

	@Bean
	@ConditionalOnProperty(name = "spring.cloud.vault.aws.credential-type", havingValue = "assumed_role")
	public AwsStsSecretBackendMetadataFactory awsStsAssumedRoleSecretBackendMetadataFactory(
			ApplicationContext context) {
		return new AwsStsSecretBackendMetadataFactory();
	}

	@Bean
	@ConditionalOnProperty(name = "spring.cloud.vault.aws.credential-type", havingValue = "federation_token")
	public AwsStsSecretBackendMetadataFactory awsStsFederationTokenSecretBackendMetadataFactory(
			ApplicationContext context) {
		return new AwsStsSecretBackendMetadataFactory();
	}

	/**
	 * {@link SecretBackendMetadataFactory} for AWS integration using
	 * {@link VaultAwsProperties}.
	 */
	public static class AwsSecretBackendMetadataFactory implements SecretBackendMetadataFactory<VaultAwsProperties> {

		/**
		 * Creates {@link SecretBackendMetadata} for a secret backend using
		 * {@link VaultAwsProperties}. This accessor transforms Vault's username/password
		 * property names to names provided with
		 * {@link VaultAwsProperties#getAccessKeyProperty()} and
		 * {@link VaultAwsProperties#getSecretKeyProperty()}.
		 * @param properties must not be {@literal null}.
		 * @return the {@link SecretBackendMetadata}
		 */
		static SecretBackendMetadata forAws(final VaultAwsProperties properties) {

			Assert.notNull(properties, "VaultAwsProperties must not be null");

			PropertyNameTransformer transformer = new PropertyNameTransformer();
			transformer.addKeyTransformation("access_key", properties.getAccessKeyProperty());
			transformer.addKeyTransformation("secret_key", properties.getSecretKeyProperty());

			return new SecretBackendMetadata() {

				@Override
				public String getName() {
					return String.format("%s with Role %s", properties.getBackend(), properties.getRole());
				}

				@Override
				public String getPath() {
					return String.format("%s/creds/%s", properties.getBackend(), properties.getRole());
				}

				@Override
				public PropertyTransformer getPropertyTransformer() {
					return transformer;
				}

				@Override
				public Map<String, String> getVariables() {

					Map<String, String> variables = new HashMap<>();

					variables.put("backend", properties.getBackend());
					variables.put("key", String.format("creds/%s", properties.getRole()));

					return variables;
				}
			};
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

	/**
	 * {@link LeasingSecretBackendMetadata} for AWS integration using with STS Role
	 * {@link VaultAwsProperties}.
	 */
	public static class AwsStsSecretBackendMetadataFactory implements SecretBackendMetadataFactory<VaultAwsProperties> {

		/**
		 * Creates {@link LeasingSecretBackendMetadata} for a secret backend for AWS STS
		 * using {@link VaultAwsProperties}. This accessor transforms Vault's
		 * username/password property names to names provided with
		 * {@link VaultAwsProperties#getAccessKeyProperty()} and
		 * {@link VaultAwsProperties#getSecretKeyProperty()}.
		 * @param properties must not be {@literal null}.
		 * @return the {@link LeasingSecretBackendMetadata}
		 */
		static LeasingSecretBackendMetadata forAws(final VaultAwsProperties properties) {

			Assert.notNull(properties, "VaultAwsProperties must not be null");
			Assert.isTrue(
					(properties.getCredentialType().name().equals(AwsCredentialType.assumed_role.name())
							|| properties.getCredentialType().name().equals(AwsCredentialType.federation_token.name())),
					"Expect credential-type to be '" + AwsCredentialType.assumed_role.name() + "' or '"
							+ AwsCredentialType.federation_token.name() + "'");

			PropertyNameTransformer transformer = new PropertyNameTransformer();
			transformer.addKeyTransformation("access_key", properties.getAccessKeyProperty());
			transformer.addKeyTransformation("secret_key", properties.getSecretKeyProperty());
			transformer.addKeyTransformation("security_token", properties.getSessionTokenKeyProperty());

			return new LeasingSecretBackendMetadata() {

				@Override
				public String getName() {
					return String.format("%s with Role %s", properties.getBackend(), properties.getRole());
				}

				@Override
				public String getPath() {
					return String.format("%s/sts/%s", properties.getBackend(), properties.getRole());
				}

				@Override
				public PropertyTransformer getPropertyTransformer() {
					return transformer;
				}

				@Override
				public Map<String, String> getVariables() {

					Map<String, String> variables = new HashMap<>();

					variables.put("backend", properties.getBackend());
					variables.put("key", String.format("sts/%s", properties.getRole()));

					return variables;
				}

				@Override
				public Mode getLeaseMode() {
					return RequestedSecret.Mode.ROTATE;
				}

			};

		}

		@Override
		public SecretBackendMetadata createMetadata(VaultAwsProperties backendDescriptor) {
			if (backendDescriptor.getCredentialType().name().equalsIgnoreCase(AwsCredentialType.iam_user.name())) {
				return AwsSecretBackendMetadataFactory.forAws(backendDescriptor);
			}
			else {
				return AwsStsSecretBackendMetadataFactory.forAws(backendDescriptor);
			}
		}

		@Override
		public boolean supports(VaultSecretBackendDescriptor backendDescriptor) {
			return backendDescriptor instanceof VaultAwsProperties;
		}

	}

}
