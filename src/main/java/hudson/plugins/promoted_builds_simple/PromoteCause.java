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

import hudson.model.Hudson;
import hudson.model.Job;
import hudson.model.Run;
import java.text.SimpleDateFormat;
import java.util.Date;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 *
 * @author gcampb2
 */
public class PromoteCause {

    public int levelValue;
    public String levelName;
    private Date date;
    private String jobName;
    private int buildNumber;
    private String user;

    @DataBoundConstructor
    public PromoteCause(String user, Run r, int levelValue, String levelName) {
        this.date = new Date();

        this.user = user;

        this.levelName = levelName;
        this.levelValue = levelValue;

        this.jobName = r.getParent().getName();
        this.buildNumber = r.number;
    }

    public int getBuildNumber() {
        return buildNumber;
    }

    public Job getJob() {
        return (Job) (Hudson.getInstance().getItem(jobName));
    }

    public Run getRun() {
        return ((Job) Hudson.getInstance().getItem(jobName)).getBuildByNumber(buildNumber);
    }

    public String getUserName() {
        if (user != null) {
            return Hudson.getInstance().getUser(user).getFullName();
        }
        return "";
    }

    public String getDate() {
        String fmt = "MMM dd, yyyy hh:mm aaa";
        return getDate(fmt);
    }

    public String getDate(String format) {
        SimpleDateFormat sdf = new SimpleDateFormat(format);
        return sdf.format(date);
    }
}
