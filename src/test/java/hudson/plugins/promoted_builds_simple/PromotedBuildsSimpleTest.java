/*
 * The MIT License
 *
 * Copyright (c) 2004-2011, Sun Microsystems, Inc., Alan Harder
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package hudson.plugins.promoted_builds_simple;

import com.gargoylesoftware.htmlunit.HttpMethod;
import com.gargoylesoftware.htmlunit.WebRequestSettings;
import com.gargoylesoftware.htmlunit.html.HtmlForm;
import hudson.EnvVars;
import hudson.FilePath;
import hudson.Launcher;
import hudson.cli.CLI;
import hudson.matrix.Axis;
import hudson.matrix.AxisList;
import hudson.matrix.MatrixBuild;
import hudson.matrix.MatrixProject;
import hudson.maven.MavenModuleSet;
import hudson.maven.MavenModuleSetBuild;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.Cause.UserCause;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.ParametersDefinitionProperty;
import hudson.model.Queue;
import hudson.model.Run;
import hudson.plugins.copyartifact.BuildFilter;
import hudson.plugins.copyartifact.CopyArtifact;
import hudson.tasks.Builder;
import hudson.tasks.ArtifactArchiver;
import java.io.IOException;
import java.net.URL;
import java.util.Arrays;
import org.apache.commons.httpclient.NameValuePair;
import org.jvnet.hudson.test.CaptureEnvironmentBuilder;
import org.jvnet.hudson.test.Bug;
import org.jvnet.hudson.test.ExtractResourceSCM;
import org.jvnet.hudson.test.HudsonTestCase;

/**
 * Test interaction of promoted-builds-simple plugin with Jenkins core.
 * @author Alan Harder
 */
public class PromotedBuildsSimpleTest extends HudsonTestCase {

    /**
     * Run a build, promoted it, verify badge shown on project page.
     */
    public void testPlugin() throws Exception {
        FreeStyleProject job = createFreeStyleProject();
        FreeStyleBuild build = job.scheduleBuild2(0, new UserCause()).get();
        PromoteAction pa = build.getAction(PromoteAction.class);
        assertNotNull("plugin should add action on all builds", pa);
        WebClient wc = new WebClient();
        wc.addRequestHeader("Referer", "/");
        wc.getPage(build, "promote/?level=3");
        assertEquals(3, pa.getLevelValue());
        // check for badge image in build history:
        assertNotNull(wc.getPage(job).getElementById("side-panel").getFirstByXPath(
                "**/img[@title='GA release']"));
    }

    /**
     * Verify PromotionLevelParameter works via HTML form, http POST and CLI.
     */
    public void testParameter() throws Exception {
        FreeStyleProject job = createFreeStyleProject();
        job.addProperty(new ParametersDefinitionProperty(
                new PromotionLevelParameter("PROMO", 2, "foo")));
        CaptureEnvironmentBuilder ceb = new CaptureEnvironmentBuilder();
        job.getBuildersList().add(ceb);

        // Run via UI (HTML form)
        WebClient wc = new WebClient();
        // Jenkins sends 405 response for GET of build page.. deal with that:
        wc.setThrowExceptionOnFailingStatusCode(false);
        wc.setPrintContentOnFailingStatusCode(false);
        HtmlForm form = wc.getPage(job, "build").getFormByName("parameters");
        assertTrue(form.getSelectByName("value").getOption(1).isDefaultSelected());
        assertTrue(form.getSelectByName("value").getOption(1).isSelected());
        form.getSelectByName("value").getOption(2).setSelected(true);
        submit(form);
        Queue.Item q = hudson.getQueue().getItem(job);
        if (q != null) q.getFuture().get();
        while (job.getLastBuild().isBuilding()) Thread.sleep(100);
        assertEquals("3", ceb.getEnvVars().get("PROMO"));
        job.getBuildersList().replace(ceb = new CaptureEnvironmentBuilder());

        // Run via HTTP POST (buildWithParameters)
        WebRequestSettings post = new WebRequestSettings(
                new URL(getURL(), job.getUrl()+"/buildWithParameters"), HttpMethod.POST);
        wc.addCrumb(post);
        post.setRequestParameters(Arrays.asList(new NameValuePair("PROMO", "1"),
                                                post.getRequestParameters().get(0)));
        wc.getPage(post);
        q = hudson.getQueue().getItem(job);
        if (q != null) q.getFuture().get();
        while (job.getLastBuild().isBuilding()) Thread.sleep(100);
        assertEquals("1", ceb.getEnvVars().get("PROMO"));
        job.getBuildersList().replace(ceb = new CaptureEnvironmentBuilder());

        // Run via CLI
        CLI cli = new CLI(getURL());
        try {
            assertEquals(0, cli.execute(
                    new String[] { "build", job.getFullName(), "-p", "PROMO=5" }));
        } finally {
            cli.close();
        }
        q = hudson.getQueue().getItem(job);
        if (q != null) q.getFuture().get();
        while (job.getLastBuild().isBuilding()) Thread.sleep(100);
        assertEquals("5", ceb.getEnvVars().get("PROMO"));
    }

    /**
     * Test option to (or not) automatically mark a build as "keep forever" when promoting.
     */
    public void testAutoKeep() throws Exception {
        // Add a level that does not auto-keep (default levels do)
        ((PromotedBuildsSimplePlugin)hudson.getPlugin("promoted-builds-simple")).getLevels().add(
                new PromotionLevel("foo", "foo.gif", false));
        FreeStyleProject job = createFreeStyleProject();
        FreeStyleBuild build = job.scheduleBuild2(0, new UserCause()).get();
        PromoteAction pa = build.getAction(PromoteAction.class);
        WebClient wc = new WebClient();
        wc.addRequestHeader("Referer", "/");
        wc.getPage(build, "promote/?level=4");
        assertEquals("foo", pa.getLevel());
        assertFalse("should not get marked \"keep forever\"", build.isKeepLog());
        wc.getPage(build, "promote/?level=2");
        assertEquals(2, pa.getLevelValue());
        assertTrue("should get marked \"keep forever\"", build.isKeepLog());
        wc.getPage(build, "promote/?level=0");
        assertNull(pa.getLevel());
        // Up for debate whether demotion should auto-not-keep:
        assertTrue("demotion should not change \"keep\" setting", build.isKeepLog());
    }

    /**
     * Test the implementation of copyartifact BuildSelector.
     */
    public void testBuildSelector() throws Exception {
        FreeStyleProject job = createFreeStyleProject();
        FreeStyleBuild build = job.scheduleBuild2(0, new UserCause()).get();
        WebClient wc = new WebClient();
        wc.addRequestHeader("Referer", "/");
        wc.getPage(build, "promote/?level=2");
        job.scheduleBuild2(0, new UserCause()).get(); // Build #2 so the latest build is not promoted
        EnvVars env = new EnvVars();
        BuildFilter filter = new BuildFilter();

        PromotedBuildSelector pbs = new PromotedBuildSelector(2); // Exact match by level
        assertEquals(1, pbs.getBuild(job, env, filter).getNumber());
        pbs = new PromotedBuildSelector(1); // Lower threshold.. build #1 still matches
        assertEquals(1, pbs.getBuild(job, env, filter).getNumber());
        pbs = new PromotedBuildSelector(3); // Too high.. no match
        assertNull(pbs.getBuild(job, env, filter));
    }

    /**
     * Verify that the copyartifact BuildSelector can be used with a single maven module.
     * ie, the build for a particular module should be found even though only the parent
     * MavenModuleSetBuild has the promotion.
     */
    public void testBuildSelectorWithMavenModule() throws Exception {
        configureDefaultMaven();
        MavenModuleSet job = createMavenProject();
        job.setGoals("clean package");
        job.setScm(new ExtractResourceSCM(getClass().getResource("maven-job.zip")));
        MavenModuleSetBuild build = job.scheduleBuild2(0, new UserCause()).get();
        assertBuildStatusSuccess(build);
        WebClient wc = new WebClient();
        wc.addRequestHeader("Referer", "/");
        wc.getPage(build, "promote/?level=2");
        job.scheduleBuild2(0, new UserCause()).get(); // Build #2 so the latest build is not promoted
        EnvVars env = new EnvVars();
        BuildFilter filter = new BuildFilter();

        PromotedBuildSelector pbs = new PromotedBuildSelector(2);
        // Select from parent ModuleSet
        assertEquals(1, pbs.getBuild(job, env, filter).getNumber());
        // Select directly from submodule
        Run<?,?> result = pbs.getBuild(job.getModule("org.jenkins-ci.plugins.test:moduleA"), env, filter);
        assertNotNull("Should find promoted submodule build", result);
        assertEquals(1, result.getNumber());
    }

    private static class TestBuilder extends Builder {
        @Override
        public boolean perform(AbstractBuild<?,?> build, Launcher launcher, BuildListener listener)
                throws InterruptedException, IOException {
            // Make some files to archive as artifacts
            FilePath ws = build.getWorkspace();
            ws.child("foo.txt").touch(System.currentTimeMillis());
            return true;
        }
    }

    /**
     * Verify that the copyartifact BuildSelector can be used with a single matrix configuration.
     * ie, the build for a particular configuration should be found when only the parent
     * project has the promotion.. promotion at configuration level can override parent promotion.
     */
    public void testBuildSelectorWithMatrixConfig() throws Exception {
        MatrixProject job = createMatrixProject();
        job.setAxes(new AxisList(new Axis("foo", "bar", "baz")));
        job.getBuildersList().add(new TestBuilder());
        job.getPublishersList().add(new ArtifactArchiver("*.txt", "", false));
        MatrixBuild build = job.scheduleBuild2(0, new UserCause()).get();
        assertBuildStatusSuccess(build);
        WebClient wc = new WebClient();
        wc.addRequestHeader("Referer", "/");
        wc.getPage(build, "promote/?level=2");
        job.scheduleBuild2(0, new UserCause()).get(); // Build #2 so the latest build is not promoted
        EnvVars env = new EnvVars();
        BuildFilter filter = new BuildFilter();

        PromotedBuildSelector pbs = new PromotedBuildSelector(2);
        // Select from parent MatrixBuild
        assertEquals(1, pbs.getBuild(job, env, filter).getNumber());
        // Select directly from configuration (MatrixRun)
        Run<?,?> result = pbs.getBuild(job.getItem("foo=bar"), env, filter);
        assertNotNull("Should find promoted configuration build", result);
        assertEquals(1, result.getNumber());
        // Now promote the particular configuration, only to level 1.. overrides parent promotion.
        wc.getPage(job, "foo=bar/1/promote/?level=1");
        assertNull(pbs.getBuild(job.getItem("foo=bar"), env, filter));
    }
    
    /**
     * Verify that the configuration of {@link PromotedBuildSelector}
     * is displayed correctly and saved correctly.
     * 
     * @throws Exception
     */
    @Bug(29767)
    public void testConfigurationForPromotedBuildSelector() throws Exception {
        for (int i = 0; i <= PromoteAction.getAllPromotionLevels().size(); ++i) {
            PromotedBuildSelector selector = new PromotedBuildSelector(i);
            
            FreeStyleProject p = createFreeStyleProject();
            p.getBuildersList().add(new CopyArtifact(
                    p.getFullName(),
                    // "",             // parameter (for copyartifact >= 1.26)
                    selector,
                    "**/*",         // filter
                    "",             // target
                    false,          // flatten
                    false           // optional
            ));
            configRoundtrip(p);
            
            p = hudson.getItemByFullName(p.getFullName(), FreeStyleProject.class);
            assertEqualDataBoundBeans(
                    selector,
                    p.getBuildersList().get(CopyArtifact.class).getBuildSelector()
            );
        }
    }
}
