/*
 * Copyright 2016 FabricMC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.quiltmc.loader.impl.metadata.qmj;

import java.util.*;

import net.fabricmc.loader.api.metadata.ModEnvironment;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.quiltmc.json5.exception.ParseException;
import org.quiltmc.loader.api.*;
import org.quiltmc.loader.api.LoaderValue.LObject;
import org.quiltmc.loader.api.LoaderValue.LType;
import org.quiltmc.loader.impl.VersionConstraintImpl;
import org.quiltmc.loader.impl.metadata.qmj.JsonLoaderValue.ArrayImpl;
import org.quiltmc.loader.impl.metadata.qmj.JsonLoaderValue.ObjectImpl;

import static org.quiltmc.loader.impl.metadata.qmj.ModMetadataReader.parseException;

// TODO: Figure out a way to not need to always specify JsonLoaderValue everywhere so we can let other users and plugins have location data.
final class V1ModMetadataReader {
	public static V1ModMetadataImpl read(JsonLoaderValue.ObjectImpl root) {
		// Read loader category
		@Nullable JsonLoaderValue quiltLoader = root.get("quilt_loader");

		if (quiltLoader == null) {
			throw new ParseException("quilt_loader field is required");
		}

		if (quiltLoader.type() != LoaderValue.LType.OBJECT) {
			throw parseException(quiltLoader, "quilt_loader field must be an object");
		}

		return readFields(root);
	}

	private static V1ModMetadataImpl readFields(JsonLoaderValue.ObjectImpl root) {
		/* Required fields */
		String id;
		String group;
		Version version = null;
		/* Optional fields */
		String name = null;
		String description = null;
		List<ModLicense> licenses = new ArrayList<>();
		List<ModContributor> contributors = new ArrayList<>();
		Map<String, String> contactInformation = new LinkedHashMap<>();
		List<ModDependency> depends = new ArrayList<>();
		List<ModDependency> breaks = new ArrayList<>();
		String intermediateMappings = null;
		Icons icons = null;
		/* Internal fields */
		ModLoadType loadType = ModLoadType.IF_REQUIRED;
		Collection<ModProvided> provides = new ArrayList<>();
		Map<String, List<AdapterLoadableClassEntry>> entrypoints = new LinkedHashMap<>();
		List<AdapterLoadableClassEntry> plugins = new ArrayList<>();
		List<String> jars = new ArrayList<>();
		Map<String, String> languageAdapters = new LinkedHashMap<>();
		List<String> repositories = new ArrayList<>();
		/* TODO: Move to plugins */
		List<String> mixins = new ArrayList<>();
		List<String> accessWideners = new ArrayList<>();
		ModEnvironment environment = ModEnvironment.UNIVERSAL;

		JsonLoaderValue.ObjectImpl quiltLoader = (JsonLoaderValue.ObjectImpl) root.get("quilt_loader");

		if (quiltLoader == null) {
			throw parseException(root, "quilt_loader is a required field");
		}

		// Loader metadata
		{
			// Check if our required fields are here
			id = requiredString(quiltLoader, "id");

			if (!Patterns.VALID_MOD_ID.matcher(id).matches()) {
				// id must be non-null
				throw parseException(Objects.requireNonNull(quiltLoader.get("id")), "Invalid mod id, likely one of the following errors:\n" +
						"- Mod id contains invalid characters, the allowed characters are a-z 0-9 _-\n" +
						"- The mod id is too short or long, the mod id must be between 2 and 63 characters");
			}

			group = requiredString(quiltLoader, "group");

			if (!Patterns.VALID_MAVEN_GROUP.matcher(group).matches()) {
				// group must be non-null
				throw parseException(Objects.requireNonNull(quiltLoader.get("id")), "Invalid mod maven group; the allowed characters are a-z A-Z 0-9 - _ and .");
			}

			// Versions
			@Nullable JsonLoaderValue versionValue = quiltLoader.get("version");

			if (versionValue == null) {
				throw new ParseException("version is a required field");
			}

			// TODO: Here we would check if the version is a placeholder in dev.

			version = Version.of(versionValue.asString());
			// Now we reach optional fields
			// TODO: provides

			@Nullable
			JsonLoaderValue entrypointsValue = quiltLoader.get("entrypoints");

			if (entrypointsValue != null) {
				if (entrypointsValue.type() != LoaderValue.LType.OBJECT) {
					throw parseException(entrypointsValue, "entrypoints must be an object");
				}

				readAdapterLoadableClassEntries((JsonLoaderValue.ObjectImpl) entrypointsValue, "entrypoints", entrypoints);
			}

			@Nullable
			JsonLoaderValue pluginsValue = quiltLoader.get("plugins");

			if (pluginsValue != null) {
				if (pluginsValue.type() != LoaderValue.LType.ARRAY) {
					throw parseException(pluginsValue, "plugins must be an array");
				}

				for (LoaderValue entry : pluginsValue.asArray()) {
					plugins.add(readAdapterLoadableClassEntry((JsonLoaderValue) entry, "plugins"));
				}
			}

			@Nullable
			JsonLoaderValue jarsValue = quiltLoader.get("jars");

			if (jarsValue != null) {
				if (jarsValue.type() != LoaderValue.LType.ARRAY) {
					throw parseException(jarsValue, "jars must be an array");
				}

				readStringList((JsonLoaderValue.ArrayImpl) jarsValue, "jars", jars);
			}

			@Nullable
			JsonLoaderValue languageAdaptersValue = quiltLoader.get("language_adapters");

			if (languageAdaptersValue != null) {
				if (languageAdaptersValue.type() != LoaderValue.LType.OBJECT) {
					throw parseException(languageAdaptersValue, "language_adapters must be an object");
				}

				readStringMap((JsonLoaderValue.ObjectImpl) languageAdaptersValue, "language_adapters", languageAdapters);
			}

			@Nullable JsonLoaderValue dependsValue = assertType(quiltLoader, "depends", LoaderValue.LType.ARRAY);
			if (dependsValue != null) {
				for (LoaderValue v : dependsValue.asArray()) {
					depends.add(readDependencyObject(true, (JsonLoaderValue) v));
				}
			}

			@Nullable JsonLoaderValue breaksValue = assertType(quiltLoader, "breaks", LoaderValue.LType.ARRAY);
			if (breaksValue != null) {
				for (LoaderValue v : breaksValue.asArray()) {
					breaks.add(readDependencyObject(false, (JsonLoaderValue) v));
				}
			}

			@Nullable
			JsonLoaderValue repositoriesValue = quiltLoader.get("repositories");

			if (repositoriesValue != null) {
				if (repositoriesValue.type() != LoaderValue.LType.ARRAY) {
					throw parseException(repositoriesValue, "repositories must be an array");
				}

				readStringList((JsonLoaderValue.ArrayImpl) repositoriesValue, "repositories", repositories);
			}

			@Nullable
			JsonLoaderValue loadTypeValue = quiltLoader.get("load_type");

			if (loadTypeValue != null) {
				if (loadTypeValue.type() != LoaderValue.LType.STRING) {
					throw parseException(loadTypeValue, "load_type must be a string");
				}

				loadType = readLoadType((JsonLoaderValue.StringImpl) loadTypeValue);
			}

			@Nullable
			JsonLoaderValue providesValue = quiltLoader.get("provides");

			if (providesValue != null) {
				if (providesValue.type() != LoaderValue.LType.ARRAY) {
					throw parseException(providesValue, "provides must be an array");
				}

				for (LoaderValue provided : providesValue.asArray()) {
					if (provided.type() == LoaderValue.LType.STRING) {
						String providedId = provided.asString();
						String providedGroup = group;
						int colon = providedId.indexOf(':');
						if (colon > 0) {
							providedGroup = providedId.substring(0, colon);
							providedId = providedId.substring(colon + 1);
						}
						provides.add(new ModProvided(providedGroup, providedId, version));
					} else if (provided.type() == LType.OBJECT) {
						JsonLoaderValue.ObjectImpl providedObj = (JsonLoaderValue.ObjectImpl) provided.asObject();

						String providedId = requiredString(providedObj, "id");
						String providedGroup = group;
						int colon = providedId.indexOf(':');
						if (colon > 0) {
							providedGroup = providedId.substring(0, colon);
							providedId = providedId.substring(colon + 1);
						}

						Version providedVersion = version;

						if (providedObj.containsKey("version")) {
							providedVersion = Version.of(requiredString(providedObj, "version"));
						}

						provides.add(new ModProvided(providedGroup, providedId, providedVersion));
					} else {
						throw parseException((JsonLoaderValue) provided, "provides must be an array containing only objects and/or strings");
					}
				}
			}

			@Nullable
			JsonLoaderValue intermediateMappingsValue = quiltLoader.get("intermediate_mappings");

			String[] supported_mappings = { "org.quiltmc:hashed", "net.fabricmc:intermediary" };
			String mappings = "org.quiltmc:hashed";

			if (intermediateMappingsValue != null) {
				if (intermediateMappingsValue.type() != LoaderValue.LType.STRING) {
					throw parseException(intermediateMappingsValue, "intermediate_mappings must be a string");
				}

				mappings = intermediateMappingsValue.asString();

				if (!Patterns.VALID_INTERMEDIATE.matcher(mappings).matches()) {
					throw parseException(intermediateMappingsValue, "intermediate_mappings must be a valid maven coordinate");
				}

				if (!Arrays.asList(supported_mappings).contains(mappings)) {
					throw parseException(intermediateMappingsValue, "unknown intermediate mappings");
				}
			}

			// Until Loader supports hashed mappings
			if (mappings.equals("org.quiltmc:hashed")) {
				throw new ParseException("Oh no! This version of Quilt Loader doesn't support hashed mappings, please update Quilt Loader to use this mod.");
			}

			intermediateMappings = mappings;

			// Metadata
			JsonLoaderValue metadataValue = quiltLoader.get("metadata");

			if (metadataValue != null) {
				if (metadataValue.type() != LoaderValue.LType.OBJECT) {
					throw parseException(metadataValue, "metadata must be an object");
				}

				JsonLoaderValue.ObjectImpl metadata = (JsonLoaderValue.ObjectImpl) metadataValue;

				name = string(metadata, "name");
				description = string(metadata, "description");

				@Nullable
				JsonLoaderValue contributorsValue = metadata.get("contributors");
				if (contributorsValue != null) {
					if (contributorsValue.type() != LType.OBJECT) {
						throw parseException(contributorsValue, "contributors must be an object");
					}
					Map<String, String> intermediate = new HashMap<>();
					readStringMap(contributorsValue.asObject(), "contributors", intermediate);
					intermediate.forEach((k, v) -> contributors.add(new ModContributorImpl(k, v)));
				}

				@Nullable
				JsonLoaderValue contact = metadata.get("contact");

				if (contact != null) {
					if (contact.type() != LoaderValue.LType.OBJECT) {
						throw parseException(contact, "contact must be an object");
					}

					readStringMap(contact.asObject(), "contact", contactInformation);
				}

				readLicenses(metadata, licenses);

				@Nullable
				JsonLoaderValue iconValue = metadata.get("icon");
				if (iconValue != null) {
					if (iconValue.type() == LType.STRING) {
						icons = new Icons.Single(iconValue.asString());
					} else if (iconValue.type() == LType.OBJECT) {
						SortedMap<Integer, String> map = new TreeMap<>();
						readIntToStringMap(iconValue.asObject(), "icon", map);
						icons = new Icons.Multiple(map);
					}
				}
			}
		}

		{
			// FIXME: These entries need to be moved when plugins are ready
			// TODO: Move mixin parsing to a plugin
			@Nullable
			JsonLoaderValue mixinValue = root.get("mixin");

			if (mixinValue != null) {
				switch (mixinValue.type()) {
				case ARRAY:
					readStringList((JsonLoaderValue.ArrayImpl) mixinValue, "mixin", mixins);
					break;
				case STRING:
					mixins.add(mixinValue.asString());
					break;
				default:
					throw parseException(mixinValue, "mixin value must be an array of strings or a string");
				}
			}

			@Nullable JsonLoaderValue mcValue = root.get("minecraft");
			if (mcValue != null) {
				ObjectImpl object = mcValue.asObject();
				String env = string(object, "environment");
				switch (env) {
					case "client":
						environment = ModEnvironment.CLIENT;
						break;
					case "dedicated_server":
						environment = ModEnvironment.SERVER;
						break;
					case "":
					case "*":
						environment = ModEnvironment.UNIVERSAL;
						break;
					default:
						throw parseException(object.get("environment"), env +
								" is not a valid environment. Valid options are \"*\", \"client\", or \"dedicated_server\"");
				}
			}

			@Nullable
			JsonLoaderValue awValue = root.get("access_widener");
			if (awValue != null) {
				switch (awValue.type()) {
					case ARRAY:
						readStringList((JsonLoaderValue.ArrayImpl) awValue, "access_widener", accessWideners);
						break;
					case STRING:
						accessWideners.add(awValue.asString());
						break;
					default:
						throw parseException(awValue, "mixin value must be an array of strings or a string");
				}
			}

		}

		return new V1ModMetadataImpl(
				root,
				id,
				group,
				version,
				name,
				description,
				licenses,
				contributors,
				contactInformation,
				depends,
				breaks,
				intermediateMappings,
				icons,
				loadType,
				provides,
				entrypoints,
				plugins,
				jars,
				languageAdapters,
				repositories,
				mixins,
				accessWideners,
				environment
		);
	}

	private static String requiredString(JsonLoaderValue.ObjectImpl object, String field) {
		JsonLoaderValue value = object.get(field);

		if (value == null) {
			throw parseException(object, String.format("%s is a required field", field));
		}

		if (value.type() != LoaderValue.LType.STRING) {
			throw parseException(value, String.format("%s must be a string", field));
		}

		return value.asString();
	}

	private static JsonLoaderValue requiredField(JsonLoaderValue.ObjectImpl object, String field) {
		JsonLoaderValue value = object.get(field);

		if (value == null) {
			throw parseException(object, String.format("%s is a required field", field));
		} else {
			return value;
		}
	}

	@NotNull
	private static String string(JsonLoaderValue.ObjectImpl object, String field) {
		JsonLoaderValue value = object.get(field);

		if (value == null) {
			return "";
		}

		if (value.type() != LoaderValue.LType.STRING) {
			throw parseException(value, String.format("%s must be a string", field));
		}

		return value.asString();
	}

	private static boolean bool(ObjectImpl object, String field, boolean _default) {
		JsonLoaderValue value = object.get(field);
		
		if (value == null) {
			return _default;
		}
		
		if (value.type() != LoaderValue.LType.BOOLEAN) {
			throw parseException(value, String.format("%s must be a boolean", field));
		}
		
		return value.asBoolean();
	}

	@Nullable
	private static JsonLoaderValue assertType(JsonLoaderValue.ObjectImpl object, String field, LoaderValue.LType type) {
		JsonLoaderValue value = object.get(field);

		if (value == null) {
			return null;
		}

		if (value.type() != type) {
			throw parseException(object, String.format("%s must be of type %s", field, type));
		}

		return value;
	}

	private static ModLoadType readLoadType(JsonLoaderValue.StringImpl value) {
		switch (value.asString()) {
			case "always":
				return ModLoadType.ALWAYS;
			case "if_possible": 
				return ModLoadType.IF_POSSIBLE;
			case "if_required":
				return ModLoadType.IF_REQUIRED;
			default:
				throw parseException(value, "load_type must be either 'always', 'if_possible', or 'if_required', but got '" + value.asString() + "'");
		}
	}

	/**
	 * Read an array as a list of strings.
	 *
	 * @param array array to read from
	 * @param inside the name of the array field
	 * @param destination the list to add the strings to
	 * @throws ParseException if any entry is not a string
	 */
	private static void readStringList(JsonLoaderValue.ArrayImpl array, String inside, List<String> destination) {
		for (LoaderValue value : array) {
			if (value.type() != LoaderValue.LType.STRING) {
				throw parseException((JsonLoaderValue) value, String.format("Entry inside %s must be a string", inside));
			}

			destination.add(value.asString());
		}
	}

	private static void readStringMap(JsonLoaderValue.ObjectImpl object, String inside, Map<String, String> destination) {
		for (Map.Entry<String, LoaderValue> entry : object.entrySet()) {
			String key = entry.getKey();
			LoaderValue value = entry.getValue();

			if (value.type() != LoaderValue.LType.STRING) {
				throw parseException((JsonLoaderValue) value, String.format("entry with key %s inside \"%s\" must be a string", key, inside));
			}

			if (destination.put(key, value.asString()) != null) {
				// TODO: Warn in dev environment about duplicate keys
			}
		}
	}

	private static void readIntToStringMap(JsonLoaderValue.ObjectImpl object, String inside, Map<Integer, String> destination) {
		object.forEach((key, value) -> {
			int keyInt;
			try {
				keyInt = Integer.parseInt(key);
			} catch (NumberFormatException ex) {
				throw parseException(object, "Key " + key + " must be an integer");
			}

			if (value.type() != LoaderValue.LType.STRING) {
				throw parseException((JsonLoaderValue) value, String.format("entry with key %s inside \"%s\" must be a string", key, inside));
			}

			if (destination.put(keyInt, value.asString()) != null) {
				// TODO: warn in dev env
			}
		});
	}

	private static void readLicenses(JsonLoaderValue.ObjectImpl metadata, List<ModLicense> licenses) {
		JsonLoaderValue licensesValue = metadata.get("license");

		if (licensesValue != null) {
			switch (licensesValue.type()) {
			case ARRAY:
				for (LoaderValue license : licensesValue.asArray()) {
					licenses.add(readLicenseObject((JsonLoaderValue) license));
				}

				break;
			case OBJECT:
			case STRING:
				licenses.add(readLicenseObject(licensesValue));
				break;
			default:
				throw parseException(licensesValue, "license field must be a string, an array or an object");
			}
		}
	}

	private static ModLicense readLicenseObject(JsonLoaderValue licenseValue) {
		switch (licenseValue.type()) {
		case OBJECT: {
			JsonLoaderValue.ObjectImpl object = licenseValue.asObject();

			String name = requiredString(object, "name");
			String id = requiredString(object, "id");
			String url = requiredString(object, "url");
			@Nullable String description = string(object, "description");

			return new ModLicenseImpl(name, id, url, description);
		}
		case STRING: {
			ModLicense ret = ModLicenseImpl.fromIdentifier(licenseValue.asString());

			if (ret == null) {
				// QMJ specification says this *must* be a valid identifier if it doesn't want to use the long-form version
				throw new ParseException("A license declared as a string id must be a valid SPDX identifier");
			}

			return ret;
		}
		default:
			throw parseException(licenseValue, "License entry must be an object or string");
		}
	}

	private static void readAdapterLoadableClassEntries(JsonLoaderValue.ObjectImpl object, String inside, Map<String, List<AdapterLoadableClassEntry>> destination) {
		for (Map.Entry<String, LoaderValue> entry : object.entrySet()) {
			String entrypointKey = entry.getKey();
			LoaderValue value = entry.getValue();

			// Add the entry if not already present
			destination.putIfAbsent(entrypointKey, new ArrayList<>());
			List<AdapterLoadableClassEntry> entries = destination.get(entrypointKey);

			switch (value.type()) {
			case ARRAY:
				for (LoaderValue entrypoint : value.asArray()) {
					entries.add(readAdapterLoadableClassEntry((JsonLoaderValue) entrypoint, inside));
				}

				break;
			case STRING:
				entries.add(readAdapterLoadableClassEntry((JsonLoaderValue) value, inside));
				break;
			default:
			}
		}
	}

	private static AdapterLoadableClassEntry readAdapterLoadableClassEntry(JsonLoaderValue entry, String inside) {
		switch (entry.type()) {
		case OBJECT:
			LoaderValue.LObject entryObject = entry.asObject();
			LoaderValue adapter = entryObject.get("adapter");
			LoaderValue value = entryObject.get("value");

			if (adapter == null) {
				throw new ParseException(String.format("entry inside \"%s\" in object form is missing the \"adapter\" field", inside));
			}

			if (value == null) {
				throw new ParseException(String.format("entry inside \"%s\" in object form is missing the \"value\" field", inside));
			}

			if (adapter.type() != LoaderValue.LType.STRING) {
				throw parseException((JsonLoaderValue) adapter, String.format("adapter field inside \"%s\" must be a string", inside));
			}

			if (value.type() != LoaderValue.LType.STRING) {
				throw parseException((JsonLoaderValue) value, String.format("adapter field inside \"%s\" must be a string", inside));
			}

			return new AdapterLoadableClassEntry(adapter.asString(), value.asString());
		case STRING:
			// Assume `default` as language adapter
			return new AdapterLoadableClassEntry("default", entry.asString());
		default:
			throw parseException(entry, String.format("value inside \"%s\" must be a string or object", inside));
		}
	}

	private static ModDependency readDependencyObject(boolean isAny, JsonLoaderValue value) {
		switch (value.type()) {
		case OBJECT:
			JsonLoaderValue.ObjectImpl obj = value.asObject();
			ModDependencyIdentifier id = new ModDependencyIdentifierImpl(requiredString(obj, "id"));
			Collection<VersionConstraint> versions = readConstraints(obj.get("versions"));
			String reason = string(obj, "reason");
			boolean optional = bool(obj, "optional", false);
			@Nullable JsonLoaderValue unlessObj = obj.get("unless");
			ModDependency unless = null;

			if (unlessObj != null) {
				unless = readDependencyObject(true, unlessObj);
			}

			return new ModDependencyImpl.OnlyImpl(value.location(), id, versions, reason, optional, unless);
		case STRING:
			// Single dependency, any version matching id
			return new ModDependencyImpl.OnlyImpl(value.location(),new ModDependencyIdentifierImpl(value.asString()));
		case ARRAY:
			// OR or all sub dependencies
			JsonLoaderValue.ArrayImpl array = value.asArray();
			Collection<ModDependency> dependencies = new ArrayList<>(array.size());

			for (LoaderValue loaderValue : array) {
				dependencies.add(readDependencyObject(isAny, (JsonLoaderValue) loaderValue));
			}

			return isAny ? new ModDependencyImpl.AnyImpl(value.location(), dependencies) : new ModDependencyImpl.AllImpl(value.location(), dependencies);
		default:
			throw parseException(
					value,
					"Dependency object must be an object or string to represent a single dependency or an array to represent any dependency"
			);
		}
	}

	private static Collection<VersionConstraint> readConstraints(@Nullable JsonLoaderValue value) {
		if (value == null) {
			return Collections.singleton(VersionConstraintImpl.ANY);
		}

		if (value.type() == LoaderValue.LType.STRING) {
			return Collections.singleton(VersionConstraintImpl.parse(value.asString()));
		} else if (value.type() == LoaderValue.LType.ARRAY) {
			Collection<VersionConstraint> ret = new ArrayList<>(value.asArray().size());

			for (LoaderValue s : value.asArray()) {
				ret.add(VersionConstraintImpl.parse(s.asString()));
			}

			return ret;
		}

		throw parseException(value, "Version constraint must be a string or array of strings");
	}

	private V1ModMetadataReader() {
	}
}
