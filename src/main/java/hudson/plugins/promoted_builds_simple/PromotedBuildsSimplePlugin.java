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

import hudson.Extension;
import hudson.FilePath;
import hudson.Plugin;
import hudson.model.Descriptor.FormException;
import hudson.model.Hudson;
import hudson.model.Job;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.model.listeners.RunListener;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import javax.servlet.ServletException;
import net.sf.json.JSONObject;
import org.apache.commons.fileupload.FileItem;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

/**
 * Simply add a link in the main page sidepanel.
 * @author Alan Harder
 */
public class PromotedBuildsSimplePlugin extends Plugin {
    private List<PromotionLevel> levels = new ArrayList<PromotionLevel>();

    @Override public void start() throws Exception {
        // Default levels (load() will replace these if customized)
        levels.add(new PromotionLevel("QA build", "qa.gif", true));
        levels.add(new PromotionLevel("QA approved", "qa-green.gif", true));
        levels.add(new PromotionLevel("GA release", "ga.gif", true));
        load();
    }

    public List<PromotionLevel> getLevels() { return levels; }

    @Override public void configure(StaplerRequest req, JSONObject formData)
            throws IOException, ServletException, FormException {
        levels.clear();
        levels.addAll(req.bindJSONToList(PromotionLevel.class, formData.get("levels")));
        save();
    }

    public void doMakePromotable(StaplerRequest req, StaplerResponse rsp) throws IOException {
        req.findAncestorObject(Job.class).checkPermission(Run.UPDATE);
        Run run = req.findAncestorObject(Run.class);
        if (run != null) {
            run.addAction(new PromoteAction());
            run.save();
            rsp.sendRedirect(
                req.getRequestURI().substring(0, req.getRequestURI().indexOf("parent/parent")));
        }
    }

    /**
     * Receive file upload from startUpload.jelly.
     * File is placed in $HUDSON_HOME/userContent directory.
     */
    public void doUpload(StaplerRequest req, StaplerResponse rsp)
            throws IOException, ServletException, InterruptedException {
        Hudson hudson = Hudson.getInstance();
        hudson.checkPermission(Hudson.ADMINISTER);
        FileItem file = req.getFileItem("badgeicon.file");
        String error = null, filename = null;
        if (file == null || file.getName().isEmpty())
            error = Messages.Upload_NoFile();
        else {
            filename = "userContent/"
                    // Sanitize given filename:
                    + file.getName().replaceFirst(".*/", "").replaceAll("[^\\w.,;:()#@!=+-]", "_");
            FilePath imageFile = hudson.getRootPath().child(filename);
            if (imageFile.exists())
                error = Messages.Upload_DupName();
            else {
                imageFile.copyFrom(file.getInputStream());
                imageFile.chmod(0644);
            }
        }
        rsp.setContentType("text/html");
        rsp.getWriter().println(
                (error != null ? error : Messages.Upload_Uploaded("<tt>/" + filename + "</tt>"))
                + " <a href=\"javascript:history.back()\">" + Messages.Upload_Back() + "</a>");
    }

    @Extension
    public static class PromotedBuildsRunListener extends RunListener<Run> {
        public PromotedBuildsRunListener() {
            super(Run.class);
        }

        @Override
        public void onCompleted(Run run, TaskListener listener) {
            Result res = run.getResult();
            if (res != Result.FAILURE && res != Result.ABORTED) {
                run.addAction(new PromoteAction());
            }
        }
    }
}
