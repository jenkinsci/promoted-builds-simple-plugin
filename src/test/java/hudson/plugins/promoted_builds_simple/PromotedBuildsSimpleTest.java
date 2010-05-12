/*
 * The MIT License
 *
 * Copyright (c) 2004-2010, Sun Microsystems, Inc., Alan Harder
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
import hudson.cli.CLI;
import hudson.model.Cause.UserCause;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.ParametersDefinitionProperty;
import hudson.model.Queue;
import java.net.URL;
import java.util.Arrays;
import org.apache.commons.httpclient.NameValuePair;
import org.jvnet.hudson.test.CaptureEnvironmentBuilder;
import org.jvnet.hudson.test.HudsonTestCase;

/**
 * Test interaction of promoted-builds-simple plugin with Hudson core.
 * @author Alan.Harder@sun.com
 */
public class PromotedBuildsSimpleTest extends HudsonTestCase {

    /**
     * Run a build, promoted it, verify badge shown on project page.
     */
    public void testPlugin() throws Exception {
        FreeStyleProject job = createFreeStyleProject();
        FreeStyleBuild build = job.scheduleBuild2(0, new UserCause()).get();
        PromoteAction pa = build.getAction(PromoteAction.class);
        assertNotNull("plugin adds action on all builds", pa);
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
        // Hudson sends 405 response for GET of build page.. deal with that:
        wc.setThrowExceptionOnFailingStatusCode(false);
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
        CLI.main(new String[] {
            "-s", getURL().toString(), "build", job.getFullName(), "-p", "PROMO=5" });
        q = hudson.getQueue().getItem(job);
        if (q != null) q.getFuture().get();
        while (job.getLastBuild().isBuilding()) Thread.sleep(100);
        assertEquals("5", ceb.getEnvVars().get("PROMO"));
    }
}
