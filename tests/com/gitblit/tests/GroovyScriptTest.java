/*
 * Copyright 2011 gitblit.com.
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
package com.gitblit.tests;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import groovy.lang.Binding;
import groovy.util.GroovyScriptEngine;

import java.io.File;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.ReceiveCommand;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import com.gitblit.GitBlit;
import com.gitblit.GitBlitException;
import com.gitblit.models.RepositoryModel;
import com.gitblit.models.TeamModel;
import com.gitblit.models.UserModel;
import com.gitblit.utils.StringUtils;

/**
 * Test class for Groovy scripts. Mostly this is to facilitate development.
 * 
 * @author James Moger
 * 
 */
public class GroovyScriptTest {

	private static final AtomicBoolean started = new AtomicBoolean(false);

	@BeforeClass
	public static void startGitblit() throws Exception {
		started.set(GitBlitSuite.startGitblit());
	}

	@AfterClass
	public static void stopGitblit() throws Exception {
		if (started.get()) {
			GitBlitSuite.stopGitblit();
		}
	}

	@Test
	public void testSendMail() throws Exception {
		MockGitblit gitblit = new MockGitblit();
		MockLogger logger = new MockLogger();
		List<ReceiveCommand> commands = new ArrayList<ReceiveCommand>();
		commands.add(new ReceiveCommand(ObjectId
				.fromString("c18877690322dfc6ae3e37bb7f7085a24e94e887"), ObjectId
				.fromString("3fa7c46d11b11d61f1cbadc6888be5d0eae21969"), "refs/heads/master"));

		test("sendmail.groovy", gitblit, logger, commands);
		assertEquals(1, logger.messages.size());
		assertEquals(1, gitblit.messages.size());
		MockMail m = gitblit.messages.get(0);
		assertEquals(5, m.toAddresses.size());
		assertTrue(m.message.contains("BIT"));
	}

	private void test(String script, MockGitblit gitblit, MockLogger logger,
			List<ReceiveCommand> commands) throws Exception {

		UserModel user = new UserModel("mock");
		RepositoryModel repository = GitBlit.self().getRepositoryModel("helloworld.git");
		repository.mailingLists.add("list@helloworld.git");

		String gitblitUrl = GitBlitSuite.url;

		File groovyDir = GitBlit.getGroovyScriptsFolder();
		GroovyScriptEngine gse = new GroovyScriptEngine(groovyDir.getAbsolutePath());

		Binding binding = new Binding();
		binding.setVariable("gitblit", gitblit);
		binding.setVariable("repository", repository);
		binding.setVariable("user", user);
		binding.setVariable("commands", commands);
		binding.setVariable("url", gitblitUrl);
		binding.setVariable("logger", logger);

		Object result = gse.run(script, binding);
		if (result instanceof Boolean) {
			if (!((Boolean) result)) {
				throw new GitBlitException(MessageFormat.format(
						"Groovy script {0} has failed!  Hook scripts aborted.", script));
			}
		}
	}

	class MockGitblit {
		List<MockMail> messages = new ArrayList<MockMail>();

		public Repository getRepository(String name) throws Exception {
			return GitBlitSuite.getHelloworldRepository();
		}

		public List<String> getStrings(String key) {
			return Arrays.asList("alpha@aaa.com", "beta@bee.com", "gamma@see.com");
		}

		public List<String> getRepositoryTeams(RepositoryModel repository) {
			return Arrays.asList("testteam");
		}

		public TeamModel getTeamModel(String name) {
			TeamModel model = new TeamModel(name);
			model.mailingLists.add("list@" + name + ".com");
			return model;
		}

		public String getString(String key, String dv) {
			return dv;
		}

		public boolean getBoolean(String key, boolean dv) {
			return dv;
		}

		public void sendMail(String subject, String message, Collection<String> toAddresses) {
			messages.add(new MockMail(subject, message, toAddresses));
		}
	}

	class MockLogger {
		List<String> messages = new ArrayList<String>();

		public void info(String message) {
			messages.add(message);
		}
	}

	class MockMail {
		final Collection<String> toAddresses;
		final String subject;
		final String message;

		MockMail(String subject, String message, Collection<String> toAddresses) {
			this.subject = subject;
			this.message = message;
			this.toAddresses = toAddresses;
		}

		@Override
		public String toString() {
			return StringUtils.flattenStrings(toAddresses, ", ") + "\n\n" + subject + "\n\n"
					+ message;
		}
	}
}