package inscaleproject;

import java.util.*;
// import java.lang.*;
import javax.microedition.midlet.*;
import javax.microedition.lcdui.*;

/**
 * @author revence
 */

class SendPending extends Form implements Runnable
{
    private Stack them;
    private String title = "";
    private StringItem label;
    private MIDlet mama;
    private Displayable prev;
    public SendPending(Stack a)
    {
        super("Pending Submissions");
        them    = a;
        title   = "Pending Submissions";
        label   = new StringItem(title, "Sending pending submissions ...");
    }

    public void run()
    {
        try
        {
            while(! them.isEmpty())
            {
                Submission nxt = (Submission) them.pop();
                //  TODO: This is where the sending happens.
            }
            Alert al = new Alert("Sent!", "All pending submissions have been sent.", null, AlertType.CONFIRMATION);
            Display.getDisplay(mama).setCurrent(al, prev);
        }
        catch(Exception e)
        {
            Alert al = new Alert("Error in Sending", Integer.toString(them.size()) + " submissions were not sent due to error: " + e.toString(), null, AlertType.ERROR);
            Display.getDisplay(mama).setCurrent(al, prev);
        }
    }

    public SendPending sendPending(MIDlet ma, Displayable p)
    {
        mama       = ma;
        prev       = p;
        Thread thd = new Thread(this);
        thd.start();
        return this;
    }

    public Alert informationAlert()
    {
        Alert al = new Alert(title, Integer.toString(them.size()) + " pending.", null, AlertType.INFO);
        return al;
    }
}

class App
{
    private Vector urls, forms;
    private Stack pending;

    public App(String xml, Stack pnd)
    {
        urls    =   new Vector();
        forms   =   new Vector();
        pending =   pnd;
    }

    public Stack getPending()
    {
        return pending;
    }

    public String[] getURLs()
    {
        String[] us = new String[urls.size()];
        urls.copyInto(us);
        return us;
    }

    public Vector getForms()
    {
        return forms;
    }
}

interface Tandem
{
    public void handOver(Tandem t, Displayable d);
}

interface HasAnswer
{
    public String getAnswer();
    public Item getField();
    public String tag();
}

interface Restartable
{
    public void restart();
    public MIDlet asMIDlet();
}

class AppForm extends Vector implements CommandListener, Tandem
{
    private static String id, prepend;
    public static boolean responds;
    private Command back, send;
    private MIDlet mama;
    private Displayable prev;
    private App application;
    private Submission subm;
    private HasAnswer[] inputs;
    
    public AppForm(String i, String p, boolean r)
    {
        id          =   i;
        responds    =   r;
        prepend     =   p;
        send        =   new Command("Send", Command.OK, 0);
        back        =   new Command("Back", Command.BACK, 1);
        subm        =   new Submission();
    }

    public String id()
    {
        return id;
    }

    public String prepend()
    {
        return prepend;
    }

    public boolean present(MIDlet m, Displayable p, App a)
    {
        Enumeration them = this.elements();
        Form dispForm    = new Form(id);
        application      = a;
        mama             = m;
        prev             = p;
        if(responds) dispForm.addCommand(send);
        dispForm.addCommand(back);
        dispForm.setCommandListener(this);
        Display.getDisplay(mama).setCurrent(dispForm);
        while(them.hasMoreElements())
        {
            try
            {
                Question qn = (Question) them.nextElement();
                inputs      = qn.fields();
                for(int notI = 0; notI < inputs.length; ++notI)
                {
                    Item got = inputs[notI].getField();
                    if(got != null)
                        dispForm.append(inputs[notI].getField());
                }
                qn.handOver(this, dispForm);
            }
            catch(Exception e)
            {
                return false;
            }
        }
        return true;
    }

    public void handOver(Tandem _, Displayable __)
    {
        Display.getDisplay(mama).setCurrent(prev);
    }

    public void commandAction(Command c, Displayable d)
    {
        if(c == back)
        {
            Display.getDisplay(mama).setCurrent(prev);
            return;
        }
        if(c == send)
        {
            for(int notI = 0; notI < inputs.length; ++notI)
            {
                subm.put(inputs[notI].tag(), inputs[notI].getAnswer());
            }
            Stack themAll      = application.getPending();
            themAll.push(subm);
            SendPending sender = new SendPending(themAll);
            sender.sendPending(mama, prev);
            return;
        }
    }
}

abstract class Question implements Tandem
{
    protected static AppForm mother;
    protected static Restartable midlet;
    protected static Displayable prev;
    public Question(Restartable m, Displayable p, AppForm f)
    {
        mother = f;
        midlet = m;
        prev   = p;
    }
    abstract public HasAnswer[] fields();
}

class Submission extends Hashtable
{
    public String asXML()
    {
        StringBuffer xml = new StringBuffer();
        Enumeration cles = this.keys();
        while(cles.hasMoreElements())
        {
            Object ans = cles.nextElement();
            xml = xml.append("<v t=\"").append(ans.toString()).append("\">").append(this.get(ans).toString()).append("</v>");
        }
        return "<sub>" + xml.toString() + "</sub>";
    }
}

class PulserQuestion extends Question implements HasAnswer
{
    private StringItem counter;
    private int count;
    
    public PulserQuestion(Restartable m, Displayable d, AppForm f)
    {
        super(m, d, f);
        count   =   0;
        counter = new StringItem("Counting Timer", "Press Start");
    }

    public String tag()
    {
        return "pulser";
    }

    public HasAnswer[] fields()
    {
        HasAnswer[] them = {this};  //  TODO: Stack or not? I’m not a Java guy.
        return them;
    }

    public Item getField()
    {
        return counter;
    }

    public String getAnswer()
    {
        return Integer.toString(count);
    }

    public void handOver(Tandem t, Displayable cur)
    {
        Display.getDisplay(midlet.asMIDlet()).setCurrent(cur);
        //  TODO: Do all the counter magic here.
        t.handOver(this, cur);
    }
}

class CountDownQuestion extends Question implements HasAnswer
{
    private StringItem counter;

    public CountDownQuestion(Restartable m, Displayable d, AppForm f)
    {
        super(m, d, f);
        counter = new StringItem("Countdown", "Press Start");
    }

    public String tag()
    {
        return "cd";
    }

    public HasAnswer[] fields()
    {
        HasAnswer[] them =   {this};    //  TODO: Stack?
        return them;
    }

    public Item getField()
    {
        return counter;
    }

    public String getAnswer()
    {
        return "";
    }

    public void handOver(Tandem t, Displayable cur)
    {
        Display.getDisplay(midlet.asMIDlet()).setCurrent(cur);
        //  TODO: Do all the countdown magic here.
        t.handOver(this, cur);
    }
}

class UpdaterQuestion extends Question implements HasAnswer
{
    private StringItem report;

    public UpdaterQuestion(Restartable m, Displayable d, AppForm f)
    {
        super(m, d, f);
        report = new StringItem("Updating", "Connecting ...");
    }

    public String tag()
    {
        return "upd";
    }

    public HasAnswer[] fields()
    {
        HasAnswer[] them = {this};
        return them;
    }

    public Item getField()
    {
        return report;
    }

    public String getAnswer()
    {
        return "";
    }

    public void handOver(Tandem t, Displayable cur)
    {
        Display.getDisplay(midlet.asMIDlet()).setCurrent(cur);
        //  TODO: Conditionally fetch an update of software (first), then of questionnaire.
        //  TODO: Record the new questionnaire, and demand a restart.
        t.handOver(this, cur);
    }
}

class TimestampQuestion extends Question implements Tandem, HasAnswer
{
    public TimestampQuestion(Restartable m, Displayable d, AppForm f)
    {
        super(m, d, f);
    }

    public HasAnswer[] fields()
    {
        HasAnswer[] them = {this};
        return them;
    }

    public Item getField()
    {
        return null;
    }

    public String getAnswer()
    {
        return "";  //  TODO. Mangle the timestamp.
    }

    public String tag()
    {
        return "t";
    }

    public void handOver(Tandem t, Displayable d)
    {
        t.handOver(this, d);
    }
}

class VHTCodeQuestion extends Question implements Tandem, HasAnswer
{
    private TextField vent;

    public VHTCodeQuestion(Restartable m, Displayable d, AppForm f)
    {
        super(m, d, f);
        vent = new TextField("VHT Code", "", 4, TextField.NUMERIC);
    }

    public HasAnswer[] fields()
    {
        HasAnswer[] them = {this};
        return them;
    }

    public String getAnswer()
    {
        return vent.getString();
    }

    public Item getField()
    {
        return vent;
    }

    public String tag()
    {
        return "vc";
    }

    public void handOver(Tandem t, Displayable d)
    {
        t.handOver(this, d);
    }
}

class DateQuestion extends Question implements Tandem, HasAnswer
{
    private DateField dateField;
    
    public DateQuestion(Restartable m, Displayable d, AppForm f)
    {
        super(m, d, f);
        dateField   =   new DateField("Enter Date", DateField.DATE);
    }

    public HasAnswer[] fields()
    {
        HasAnswer[] them = {this};
        return them;
    }

    public void handOver(Tandem t, Displayable d)
    {
        t.handOver(this, d);
    }

    public String tag()
    {
        return "date";
    }

    public Item getField()
    {
        return dateField;
    }

    public String getAnswer()
    {
        return dateField.getDate().toString();  //  TODO: Remember to mangle this.
    }
}

//  TODO: Implement the Question overrides for `few`, `int`, and `choice`.

public class inSCALE extends MIDlet implements CommandListener, Restartable {

    private String appdescr;
    private List mainMenu;
    private Command pending, quitbut, formChoice;
    private Stack already = new Stack();
    //  TODO: Fit `already` with the contents of the pending submissions.
    private App application;

    private String loadDescription()
    {
        //  TODO: Load into appdescr the string of the last app description downloaded. If not present, download.
        return appdescr;
    }

    private void paint(List l, Vector fs)
    {
        Enumeration them = fs.elements();
        while(them.hasMoreElements())
        {
            AppForm fm = (AppForm) them.nextElement();
            l.append(fm.id(), null);
        }
    }

    public void startApp()
    {
        restart();
    }

    public MIDlet asMIDlet()
    {
        return this;
    }

    public void restart()
    {
        mainMenu        = new List("inSCALE", List.EXCLUSIVE | List.IMPLICIT);
        pending         = new Command("Pending", "Check and send pending messages.", Command.OK, 1);
        quitbut         = new Command("Quit", "Exit the inSCALE Project's phone application.", Command.EXIT, 0);
        formChoice      = new Command("Run", "Execute the selected questionnaire/tool.", Command.ITEM, 1);
        application     = new App(this.loadDescription(), already);
        paint(mainMenu, application.getForms());
        mainMenu.addCommand(pending);
        mainMenu.addCommand(quitbut);
        mainMenu.addCommand(formChoice);
        mainMenu.setCommandListener(this);
        Display.getDisplay(this).setCurrent(mainMenu);
    }

    public void commandAction(Command c, Displayable d)
    {
        if(c == quitbut)
        {
            this.notifyDestroyed();
        }
        else if(c == pending)
        {
            SendPending sender = new SendPending(already);
            Display.getDisplay(this).setCurrent(sender.informationAlert(), sender.sendPending(this, d));
        }
        else if(c == formChoice)
        {
            int seld        = mainMenu.getSelectedIndex();
            AppForm theForm = (AppForm) application.getForms().elementAt(seld);
            theForm.present(this, d, application);
        }
    }

    public void pauseApp()
    {

    }

    public void destroyApp(boolean unconditional)
    {

    }
}
