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

package org.quiltmc.test;

import net.fabricmc.loader.api.VersionParsingException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.quiltmc.json5.JsonReader;
import org.quiltmc.loader.api.VersionFormatException;
import org.quiltmc.loader.impl.metadata.qmj.SemanticVersionImpl;
import org.quiltmc.loader.impl.util.version.FabricSemanticVersionImpl;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;

public class VersionParsingTests extends JsonTestBase {
	static void quilt(String raw) {
		System.out.println("Checking pass: " + raw);
		try {
			SemanticVersionImpl.of(raw);
		} catch (VersionFormatException e) {
			Assertions.fail(e);
		} catch (Throwable t) {
			Assertions.fail("Uncaught exception ", t);
		}
	}
	static void quiltFails(String raw) {
		System.out.println("Checking fails: " + raw);
		try {
			SemanticVersionImpl.of(raw);
			Assertions.fail("invalid version " + raw + " was parsed successfully?");
		} catch (VersionFormatException e) {
			//
		}
	}

	static void fabric(String raw) {
		System.out.println("Checking pass: " + raw);
		try {
			new FabricSemanticVersionImpl(raw, false);
		} catch (VersionParsingException e) {
			Assertions.fail(e);
		}
	}

	static void fabricFails(String raw) {
		System.out.println("Checking fails: " + raw);
		try {
			new FabricSemanticVersionImpl(raw, false);
			Assertions.fail("Invalid version " + raw + " was parsed successfully?");
		} catch (VersionParsingException e) {
			//
		}
	}
	@Test
	void quiltParser() throws IOException {
		JsonReader pass = get("testing/version/passing.json");
		pass.beginArray();
		while (pass.hasNext()) {
			quilt(pass.nextString());
		}
		pass.close();

		JsonReader fail = get("testing/version/failing.json");
		fail.beginArray();
		while (fail.hasNext()) {
			quiltFails(fail.nextString());
		}
		fail.close();

		JsonReader fabricOnly = get("testing/version/fabric-passing-only.json");
		fabricOnly.beginArray();
		while (fabricOnly.hasNext()) {
			quiltFails(fabricOnly.nextString());
		}
		fabricOnly.close();
	}

	@Test
	void fabricParser() throws IOException {
		JsonReader pass = get("testing/version/passing.json");
		pass.beginArray();
		while (pass.hasNext()) {
			fabric(pass.nextString());
		}
		pass.close();

		JsonReader fail = get("testing/version/failing.json");
		fail.beginArray();
		while (fail.hasNext()) {
			fabricFails(fail.nextString());
		}
		fail.close();

		JsonReader fabricOnly = get("testing/version/fabric-passing-only.json");
		fabricOnly.beginArray();
		while (fabricOnly.hasNext()) {
			fabric(fabricOnly.nextString());
		}
		fabricOnly.close();
	}

}
