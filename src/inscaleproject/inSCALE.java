package inscaleproject;

import java.io.*;
import java.util.*;
import javax.microedition.io.*;
import javax.microedition.lcdui.*;
import javax.microedition.media.*;
import javax.microedition.midlet.*;
import javax.microedition.rms.*;
import javax.wireless.messaging.*;

import javax.xml.parsers.*;
import org.xml.sax.*;
import org.xml.sax.helpers.DefaultHandler;

/**
 * @author Revence Kalibwani
 */

class Tools
{
    public static String oh(long x)
    {
        if(x < 10) return "0" + Long.toString(x);
        return Long.toString(x);
    }
}

class SendPending extends Form implements Runnable, Tandem, CommandListener
{
    private String title, response;
    private StringItem label;
    private MIDlet mama;
    private Displayable prev;
    private App app;
    private Command back, retry;
    private Thread current;
    private boolean running;
    
    public SendPending(Displayable p, MIDlet m, App a)
    {
        super("Pending Submissions");
        app         = a;
        mama        = m;
        prev        = p;
        running     = false;
        title       = "Pending Submissions";
        response    = null;
        label       = new StringItem(title, "Sending pending submissions ...");
        back        = new Command("Back", Command.BACK, 0);
        retry       = new Command("Retry", Command.OK, 0);
        this.append(label);
        this.addCommand(back);
        //  this.addCommand(retry);
        this.setCommandListener(this);
    }

    public void commandAction(Command c, Displayable d)
    {
        if(c == back)
        {
            Display.getDisplay(mama).setCurrent(prev);
        }
        else if(c == retry)
        {
            if(running) return;
            if(current == null) current = new Thread(this);
            current.start();
        }
    }

    public void run()
    {
        running       = true;
        try{this.removeCommand(retry);}catch(Exception ex){}
        Stack them    = app.getPending();
        try
        {
            Vector urls   = app.getURLs();
            String err    = "";
            int tries     = them.size();
            while((!them.isEmpty()) && tries > 0)
            {
                Submission nxt  = (Submission) them.peek();
                String escXML   = URLUTF8Encoder.encode(nxt.asXML(app));
                byte[] query    = ("message=" + escXML).getBytes();
                for(int notI = 0; notI < urls.size(); ++notI)
                {
                    String u = (String) urls.elementAt(notI);
                    label.setLabel("Connecting ...");
                    label.setText(u);
                    try
                    {
                        if(u.startsWith("http://"))
                        {
                            HttpConnection hc = (HttpConnection) Connector.open(u);
                            hc.setRequestMethod(HttpConnection.POST);
                            hc.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
                            hc.setRequestProperty("Content-Length", Integer.toString(query.length));
                            OutputStream outs = hc.openOutputStream();
                            outs.write(query);
                            outs.flush();
                            if(hc.getResponseCode() != 200)
                                throw new Exception(hc.getResponseMessage());
                            else
                            {
                                try
                                {
                                    InputStream ins = hc.openInputStream();
                                    byte[] resp = new byte[(int) hc.getLength()];
                                    ins.read(resp);
                                    response    = new String(resp);
                                    ins.close();
                                }
                                catch(Exception e) {}
                                hc.close();
                            }
                            outs.close();
                        }
                        else    //  sms://+....
                        {
                            MessageConnection mc = (MessageConnection) Connector.open(u);
                            TextMessage       tm = (TextMessage) mc.newMessage(MessageConnection.TEXT_MESSAGE);
                            tm.setPayloadText(escXML);
                            mc.send(tm);
                            mc.close();
                        }
                        Display.getDisplay(mama).vibrate(500);
                        them.pop();
                        break;
                    }
                    catch(Exception e)
                    {
                          err = e.getMessage();
                          label.setLabel(err);
                          label.setText("Failed to submit to " + u);
                    }
                    --tries;
                }
                if(them.size() != 0)
                {
                    label.setLabel("Message sending failed.");
                    label.setText(err + " Failed to submit after " + Integer.toString(urls.size()) + " attempts.");
                    throw new Exception(err + " Failed to submit after " + Integer.toString(urls.size()) + " attempts.");
                }
                else
                {
                    label.setLabel("Sent!");
                    label.setText("All pending submissions have been sent. " + Integer.toString(them.size()) + " left.");
                    break;
                }
            }
        }
        catch(Exception e)
        {
            //  e.printStackTrace();
            Stack us = app.getPending();
            label.setLabel("Error in Sending");
            label.setText(Integer.toString(us.size()) + " submissions were not sent due to error: " + e.toString());
            try{this.addCommand(retry);}catch(Exception ex){}
        }
        app.savePending(them);
        Display disp = Display.getDisplay(mama);
        disp.flashBacklight(5000);
        disp.vibrate(1000);
        try
        {
            Thread.sleep(5000);
        }
        catch(Exception e) {}
        if(response != null)
        {
            Alert conf = new Alert("Sent Successfully", response, null, AlertType.INFO);
            conf.setTimeout(Alert.FOREVER);
            Display.getDisplay(mama).setCurrent(conf, prev);
        }
        else
        {
            Display.getDisplay(mama).setCurrent(prev);
        }
        //  prev = null;
        running = false;
    }

    public boolean sendPending()
    {
        Stack them = app.getPending();
        if(them.size() < 1) return false;
        if(current == null) current = new Thread(this);
        if(!running) current.start();
        return running;
    }

    public void handOver(Tandem t, Displayable d)
    {
        //  prev = d;
    }

    public Alert informationAlert()
    {
        Stack them = app.getPending();
        Alert al = new Alert(title, Integer.toString(them.size()) + " pending.", null, AlertType.INFO);
        al.setTimeout(Alert.FOREVER);
        return al;
    }

    public Displayable sendingProcess(Displayable p)
    {
        prev    =   p;
        return this;
    }
}

class Alarm implements Runnable
{
    private String location;
    private MIDlet mama;
    private InputStream ins;
    private Player ply;
    private static Alarm singleton;

    public static Alarm getAlarm(MIDlet m)
    {
        if(singleton == null)
        {
            singleton = new Alarm(m, "/alarma.wav");
        }
        return singleton;
    }

    public void close()
    {
        ply.close();
        try
        {
            ins.close();
        }
        catch(Exception e) {}
    }

    private Alarm(MIDlet m, String file)
    {
        location = file;
        mama     = m;

        ins = getClass().getResourceAsStream(location);
        try
        {
            ply = Manager.createPlayer(ins, "audio/X-wav");
        }
        catch(IOException ioe) {}
        catch(MediaException me) {}
    }

    public void run()
    {
    }

    public void ring()
    {
        Display disp = Display.getDisplay(mama);
        long effectDuration = 5 /* seconds */ * 1000 /* in milliseconds */;
        disp.flashBacklight((int) effectDuration);
        disp.vibrate((int) effectDuration);
        try
        {
            ply.start();
        }
        catch(Exception e) {}
    }
}

class App
{
    private Vector urls, forms;
    private Stack pending;
    //  private Restartable restbl;
    private Displayable dispy;
    private String status;
    private Alarm alarm;
    private MIDlet mama;
    private SendPending sender;
    private UpdaterQuestion updater;

    public App(MIDlet m)
    {
        mama    =   m;
        urls    =   new Vector();
        alarm   =   Alarm.getAlarm(m);
        status  =   null;
    }

    public void setUpdater(UpdaterQuestion uq)
    {
        updater = uq;
    }

    public void setSender(SendPending s)
    {
        sender = s;
    }

    public SendPending getSender()
    {
        return sender;
    }

    public String version()
    {
        String ans = mama.getAppProperty("MIDlet-Jar-SHA1");
        if(ans == null) return "original";
        return ans;
    }

    public void status(String s)
    {
        byte[] them = s.getBytes();
        try
        {
            RecordStore rs  =   RecordStore.openRecordStore("metadata", true);
            try
            {
                rs.setRecord(1, them, 0, them.length);
            }
            catch(InvalidRecordIDException irie)
            {
                rs.addRecord(them, 0, them.length);
            }
            rs.closeRecordStore();
        }
        catch(RecordStoreException rse)
        {
            //  rse.printStackTrace();
        }
    }

    public String date()
    {
        String ans = mama.getAppProperty("MIDlet-Creation-Date");
        if(ans == null) return "original";
        return ans;
    }

    public String status()
    {
        if(status == null)
        {
            try
            {
                RecordStore verstat =   RecordStore.openRecordStore("metadata", true);
                status              =   new String(verstat.getRecord(1));
                verstat.closeRecordStore();
            }
            catch(Exception e)
            {
                //  e.printStackTrace();
                return "default";
            }
        }
        return status;
    }

    public void initialiseForms(String x, Stack pnd, Restartable r, Displayable d)
    {
        pending         =   pnd;
        //  restbl  =   r;
        dispy           =   d;
        forms           =   new Vector();
        byte[] xmlBytes =   x.getBytes();
        SAXParserFactory spf = SAXParserFactory.newInstance();
        try
        {
            SAXParser prs           = spf.newSAXParser();
            UpdateXMLHandler uxh    = new UpdateXMLHandler(this, r, d, alarm, false);
            prs.parse(new ByteArrayInputStream(xmlBytes), uxh);
        }
        catch(Exception e)
        {
            //  e.printStackTrace();
            Alert al = new Alert("XML Parser Failure", e.getMessage(), null, AlertType.ERROR);
            al.setTimeout(Alert.FOREVER);
            Display.getDisplay(r.asMIDlet()).setCurrent(al, dispy);
        }
    }

    public void addURL(String u)
    {
        urls.addElement(u);
    }

    public void addForm(AppForm f)
    {
        forms.addElement(f);
    }

    public void setPending(Stack p)
    {
        pending = p;
    }

    public void savePending(Stack them)
    {
        pending = them;
        this.savePending();
    }

    public void savePending()
    {
        StringBuffer x = new StringBuffer("<pending>");
        for(int notI = 0, sz = pending.size(); notI < sz; ++notI)
        {
            Submission s = (Submission) pending.elementAt(notI);
            x.append(s.asXML(this));
        }
        try
        {
            RecordStore pd = RecordStore.openRecordStore("pending", true);
            byte[] data    = (x.toString() + "</pending>").getBytes();
            try
            {
                pd.setRecord(1, data, 0, data.length);
            }
            catch(InvalidRecordIDException irie)
            {
                pd.addRecord(data, 0, data.length);
            }
            pd.closeRecordStore();
        }
        catch(Exception e)
        {
            //  e.printStackTrace();
        }
    }

    public Stack getPending()
    {
        return pending;
    }

    public Vector getURLs()
    {
        return urls;
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
    public String validate();
}

interface Restartable
{
    public void restart();
    public MIDlet asMIDlet();
}

class AppForm extends Vector implements CommandListener, Tandem
{
    private String id, prepend;
    public  boolean responds, presented;
    private Command back, send;
    private MIDlet mama;
    private Displayable prev;
    private App application;
    private Submission subm;
    private HasAnswer[] inputs;
    private Vector allInputs;
    private Form dispForm;
    
    public AppForm(Displayable pr, String i, String p, boolean r)
    {
        id          =   i;
        responds    =   r;
        prepend     =   p;
        prev        =   pr;
        send        =   new Command("Send", Command.OK, 0);
        back        =   new Command("Back", Command.BACK, 1);
        subm        =   new Submission(this);
        presented   =   false;
        allInputs   =   new Vector();
    }

    public String id()
    {
        return id;
    }

    public String prepend()
    {
        return prepend;
    }

    public Form displayForm()
    {
        return dispForm;
    }

    public void present(MIDlet m, Displayable p, App a)
    {
        if(! presented)
        {
            application      = a;
            mama             = m;
            prev             = p;
            dispForm         = new Form(id);
            subm.submitterVersion(application.version());
            if(responds) dispForm.addCommand(send);
            dispForm.addCommand(back);
            dispForm.setCommandListener(this);
            Display.getDisplay(mama).setCurrent(dispForm);
            for(int notI = 0; notI < this.size(); ++notI)
            {
                Question qn = (Question) this.elementAt(notI);
                inputs      = qn.fields();
                for(int notJ = 0; notJ < inputs.length; ++notJ)
                {
                    allInputs.addElement(inputs[notJ]);
                    Item got = inputs[notJ].getField();
                    if(got != null)
                    {
                        dispForm.append(got.getLabel());
                        got.setLabel("");
                        dispForm.append(got);
                    }
                }
                qn.handOver(this, dispForm);
            }
            presented = true;
        }
        else
        {
            for(int notI = 0; notI < this.size(); ++notI)
            {
                Question qn = (Question) this.elementAt(notI);
                qn.reset();
            }
            Display.getDisplay(mama).setCurrent(dispForm);
        }
    }

    public void handOver(Tandem t, Displayable d)
    {

    }

    public String completeValidation()
    {
        String val = null;
        for(int notI = 0; val == null && notI < allInputs.size(); ++notI)
        {
            HasAnswer ha        = (HasAnswer) allInputs.elementAt(notI);
            val = ha.validate();
        }
        return val;
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
            String val = completeValidation();
            if(val != null)
            {
                Alert al = new Alert("Errors", val, null, AlertType.ERROR);
                al.setTimeout(Alert.FOREVER);
                Display.getDisplay(mama).setCurrent(al);
                return;
            }
            for(int notI = 0; notI < allInputs.size(); ++notI)
            {
                HasAnswer ha        = (HasAnswer) allInputs.elementAt(notI);
                subm.put(ha.tag(), ha.getAnswer());
            }
            Stack themAll = application.getPending();
            themAll.push(subm);
            application.savePending(themAll);
            SendPending sender = application.getSender();
            Alert alt          = sender.informationAlert();
            if(sender.sendPending())
            {
                Display.getDisplay(mama).setCurrent(alt, sender.sendingProcess(prev));
            }
            else
            {
                Display.getDisplay(mama).setCurrent(alt, prev);
            }
        }
    }
}

abstract class Question implements Tandem
{
    protected AppForm mother;
    protected Restartable midlet;
    protected Displayable prev;
    protected App originalApp;

    public Question(Restartable m, Displayable p)
    {
        midlet      = m;
        prev        = p;
    }

    public void setForm(AppForm af)
    {
        mother = af;
    }

    public void setApp(App a)
    {
        originalApp = a;
    }

    public App getApp()
    {
        return originalApp;
    }

    public AppForm getForm()
    {
        return mother;
    }

    abstract public HasAnswer[] fields();
    abstract public void reset();
}

class QuestionAnswerPair
{
    private String question, answer;
    private boolean executes;

    public QuestionAnswerPair(String q, String a)
    {
        question = q;
        answer   = a;
        executes = false;
    }

    public QuestionAnswerPair(String q, String a, boolean ex)
    {
        question = q;
        answer   = a;
        executes = ex;
    }

    public String question()
    {
        return question;
    }

    public String answer()
    {
        return answer;
    }

    public boolean executes()
    {
        return executes;
    }
}

class ChoiceQuestion extends Question implements HasAnswer
{
    private ChoiceGroup chg;
    private String label, qnid;
    private Vector options;
    
    public ChoiceQuestion(Restartable r, Displayable d, String l, String q)
    {
        super(r, d);
        qnid    =   q;
        label   =   l;
        options =   new Vector(5, 5);
        chg     =   new ChoiceGroup(l, ChoiceGroup.EXCLUSIVE);
    }

    public String validate()
    {
        return null;
    }

    public void add(QuestionAnswerPair qap)
    {
        options.addElement(qap);
        chg.append(qap.question(), null);
    }

    public HasAnswer[] fields()
    {
        HasAnswer[] them = {this};
        return them;
    }

    public Item getField()
    {
        return chg;
    }

    public String getAnswer()
    {
        int pos = chg.getSelectedIndex();
        QuestionAnswerPair qap  =   (QuestionAnswerPair) options.elementAt(pos);
        return qap.answer();
    }

    public String tag()
    {
        return qnid;
    }

    public void handOver(Tandem t, Displayable d)
    {

    }
    
    public void reset()
    {
        
    }
}

class HelpQuestion extends Question implements HasAnswer, ItemCommandListener
{
    protected ChoiceGroup chg;
    protected Command explain;
    protected Vector helpItems;
    
    public HelpQuestion(Restartable r, Displayable d, String l)
    {
        super(r, d);
        chg         =   new ChoiceGroup(l, ChoiceGroup.HYPERLINK);
        explain     =   new Command("Get Help", Command.OK, 0);
        chg.setDefaultCommand(explain);
        helpItems   =   new Vector(5, 5);
    }

    public String validate()
    {
        return null;
    }
    
    public void add(QuestionAnswerPair qap)
    {
        chg.append(qap.question(), null);
        helpItems.addElement(qap);
    }

    public String tag()
    {
        return "?";
    }

    public void commandAction(Command c, Item it)
    {
        int seld                =   chg.getSelectedIndex();
        QuestionAnswerPair qa   =   (QuestionAnswerPair) helpItems.elementAt(seld);
        if(qa.executes())
        {
            try
            {
                midlet.asMIDlet().platformRequest(qa.answer());
            }
            catch(Exception e)
            {
                //  e.printStackTrace();
                Alert al = new Alert("Failure", e.getMessage(), null, AlertType.ERROR);
                Display.getDisplay(midlet.asMIDlet()).setCurrent(al);
            }
        }
        else
        {
            Alert al = new Alert(qa.question(), qa.answer(), null, AlertType.INFO);
            al.setTimeout(Alert.FOREVER);
            Display.getDisplay(midlet.asMIDlet()).setCurrent(al);
        }
    }

    public Item getField()
    {
        return chg;
    }

    public String getAnswer()
    {
        return "";
    }

    public void handOver(Tandem t, Displayable d)
    {
        //  d.addCommand(explain);
        //  d.setCommandListener(this);
        chg.setItemCommandListener(this);
    }

    public void reset()
    {

    }

    public HasAnswer[] fields()
    {
        HasAnswer[] them = {this};
        return them;
    }
}

class FewQuestion extends Question implements HasAnswer, Tandem
{
    protected TextField input;
    private String label, qnid;

    public FewQuestion(Restartable r, Displayable d, String l, String q)
    {
        super(r, d);
        label   =   l;
        qnid    =   q;
        input   =   new TextField(label, "", 2, TextField.NUMERIC);
    }

    public String validate()
    {
        if(input.getString().length() < 1)
            return "Provide data for " + label;
        return null;
    }
    
    public HasAnswer[] fields()
    {
        HasAnswer[] ha = {this};
        return ha;
    }

    public String tag()
    {
        return qnid;
    }

    public String getAnswer()
    {
        return input.getString();
    }

    public Item getField()
    {
        return input;
    }

    public void handOver(Tandem t, Displayable d)
    {
        
    }

    public void reset()
    {
        
    }
}

class NumberQuestion extends FewQuestion
{
    public NumberQuestion(Restartable r, Displayable d, String l, String q)
    {
        super(r, d, l, q);
        input.setMaxSize(10);
    }
}

class Submission extends Hashtable
{
    private AppForm form;
    private String ver;

    public Submission(AppForm f)
    {
        form = f;
        ver  = "unspecified";
    }

    public void submitterVersion(String v)
    {
        ver = v;
    }

    public String submitterVersion()
    {
        return ver;
    }

    public String asXML(App app)
    {
        StringBuffer xml = new StringBuffer();
        Enumeration cles = this.keys();
        while(cles.hasMoreElements())
        {
            Object ans = cles.nextElement();
            xml.append("<v t=\"").append(ans.toString()).append("\">").append(this.get(ans).toString()).append("</v>");
        }
        return form.prepend() + "<sub av=\"" + app.version() + "\" sv=\"" + this.submitterVersion() + "\">" + xml.toString() + "</sub>";
    }
}

class LinkQuestion extends Question implements HasAnswer, ItemCommandListener
{
    private String href, text;
    private StringItem linkField;

    public LinkQuestion(Restartable m, Displayable d, String h, String t)
    {
        super(m, d);
        href        =   h;
        text        =   t;
        linkField   =   new StringItem(href, text, StringItem.HYPERLINK);
        linkField.setItemCommandListener(this);
    }

    public void commandAction(Command c, Item i)
    {
        try
        {
            midlet.asMIDlet().platformRequest(href);
        }
        catch(Exception e)
        {
            Alert al = new Alert("Link Error", "Could not execute the link: " + href, null, AlertType.ERROR);
            Display.getDisplay(midlet.asMIDlet()).setCurrent(al);
        }
    }

    public String tag()
    {
        return "a";
    }

    public String getAnswer()
    {
        return "";
    }

    public Item getField()
    {
        return linkField;
    }

    public String validate()
    {
        return null;
    }

    public void reset()
    {

    }

    public HasAnswer[] fields()
    {
        HasAnswer[] them = {this};
        return them;
    }

    public void handOver(Tandem t, Displayable d)
    {

    }

}
class SayQuestion extends Question implements HasAnswer
{
    private boolean underline, bold, italic;
    private StringItem sayField;

    public SayQuestion(Restartable m, Displayable d, boolean b, boolean i, boolean u, String t)
    {
        super(m, d);
        underline   =   u;
        bold        =   b;
        italic      =   i;
        sayField    =   new StringItem("", t);
        sayField.setFont(Font.getFont(Font.FACE_MONOSPACE,
            (bold   ? Font.STYLE_BOLD : Font.STYLE_PLAIN) |
            (italic ? Font.STYLE_ITALIC : Font.STYLE_PLAIN) |
            (underline ? Font.STYLE_UNDERLINED : Font.STYLE_PLAIN),
        Font.SIZE_MEDIUM));
    }

    public String tag()
    {
        return "say";
    }

    public String getAnswer()
    {
        return "";
    }

    public Item getField()
    {
        return sayField;
    }

    public String validate()
    {
        return null;
    }

    public void reset()
    {

    }

    public HasAnswer[] fields()
    {
        HasAnswer[] them = {this};
        return them;
    }

    public void handOver(Tandem t, Displayable d)
    {

    }

}

class PulserQuestion extends Question implements HasAnswer, CommandListener, Runnable
{
    private StringItem counter;
    private int count;
    private Command start, inc;
    private long duration;
    private String label, note;
    private Thread interrupted, currentThread;
    private boolean stillCounting;
    private Alarm alarm;
    private Displayable moi;
    
    public PulserQuestion(Restartable m, Displayable d, Alarm alm, String t, String n, long s)
    {
        super(m, d);
        count           =   0;
        duration        =   s * 1000;
        label           =   t;
        note            =   n;
        interrupted     =   null;
        stillCounting   =   false;
        alarm           =   alm;
        counter         =   new StringItem(label, "reset()");
        inc             =   new Command("+1", Command.OK, 0);
        start           =   new Command("Start", Command.OK, 0);
        //  counter.setText("To begin counting, press the Start button.");

        //  reset();
    }

    public String tag()
    {
        return "pulser";
    }

    public String validate()
    {
        return null;
    }
    
    public HasAnswer[] fields()
    {
        HasAnswer[] them = {this};
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
        reset();
        moi =   cur;
        cur.addCommand(start);
        cur.setCommandListener(this);
    }

    public void reset()
    {
        stillCounting = true;
        count         = 0;
        interrupted   = null;
        counter.setText("To begin counting, press the Start button.");

        currentThread = new Thread(this);
        currentThread.start();
    }

    public void run()
    {
        try
        {
            Thread.sleep(duration);
        }
        catch(Exception e)
        {
            interrupted = Thread.currentThread();
        }
        if(Thread.currentThread() == interrupted)
            return;
        if(Thread.currentThread() != currentThread)
            return;
        //  Because we stupidly call this every time the app starts up. I don’t have the time to re-design it.
        try {moi.removeCommand(inc);} catch(Exception e) {/*e.printStackTrace();*/}
        stillCounting = false;
        commandAction(null, moi);
    }

    public void commandAction(Command c, Displayable d)
    {
        if(c == start)
        {
            d.removeCommand(start);
            counter.setLabel("Counting: ");
            d.addCommand(inc);
        }
        if((c == start || c == inc) && stillCounting)
        {
            ++count;
            Display.getDisplay(midlet.asMIDlet()).vibrate(250);
            counter.setText(Integer.toString(count));
        }
        else
        {
            try{moi.removeCommand(inc);}catch(Exception e){ /*e.printStackTrace();*/}   //  Because we foolishly over-use this in different situations.
            alarm.ring();
            interrupted = currentThread;
            Alert sofar = new Alert("Result: " + Integer.toString(count), Integer.toString(count) + " counts.\n\n" + note, null, AlertType.INFO);
            sofar.setTimeout(Alert.FOREVER);
            sofar.setCommandListener(new CommandListener()
            {
                public void commandAction(Command cc, Displayable dd)
                {
                    moi.addCommand(start);
                    Display.getDisplay(midlet.asMIDlet()).setCurrent(prev);
                }
            });
            Display.getDisplay(midlet.asMIDlet()).setCurrent(sofar);
        }
    }
}

class CountDownQuestion extends Question implements HasAnswer, Runnable, CommandListener
{
    private StringItem counter;
    private String title, note;
    private long duration, remainder;
    private Command bg, start;
    private Alarm alarm;
    private Displayable moi;
    private boolean hidden, active;

    public CountDownQuestion(Restartable m, Displayable d, Alarm alm, String t, String n, long ds)
    {
        super(m, d);
        title       = t;
        note        = n;
        duration    = ds * 1000;
        bg          = new Command("Hide", Command.SCREEN, 0);
        start       = new Command("Start", Command.OK, 0);
        counter     = new StringItem(title, "reset()");
        alarm       = alm;
        hidden      = false;
        //  reset();
    }

    public String validate()
    {
        return null;
    }
    
    public String tag()
    {
        return "cd";
    }

    public HasAnswer[] fields()
    {
        HasAnswer[] them =   {this};
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

    public void run()
    {
        long pause = 1000;
        try
        {
            for(long mins = 0; remainder > 0 && active; remainder = remainder - pause)
            {
                repaint();
                Thread.sleep(pause);
                mins = (++mins % 60);
                if(mins != 0) continue;
                Display.getDisplay(midlet.asMIDlet()).vibrate(500);
                Display.getDisplay(midlet.asMIDlet()).flashBacklight(500);
            }
        }
        catch(Exception e) {}
        if(! active)
            return;
        if(hidden)
        {
            Display.getDisplay(midlet.asMIDlet()).setCurrent(moi);
        }
        hidden = false;
        commandAction(null, moi);
    }

    public void commandAction(Command c, Displayable d)
    {
        if(c == bg)
        {
            Alert alt = new Alert("Background", "The timer will sound an alarm when the time is up.", null, AlertType.CONFIRMATION);
            alt.setTimeout(Alert.FOREVER);
            Display.getDisplay(midlet.asMIDlet()).setCurrent(alt, prev);
            hidden = true;
        }
        else if(c == start)
        {
            moi.removeCommand(start);
            moi.addCommand(bg);
            active = true;
            Thread thd = new Thread(this);
            thd.start();
        }
        else
        {
            active = false;
            alarm.ring();
            Alert sofar = new Alert("Timer alarm!", note, null, AlertType.ALARM);
            sofar.setTimeout(Alert.FOREVER);
            Display.getDisplay(midlet.asMIDlet()).setCurrent(sofar, prev);
        }
    }

    private void repaint()
    {
        StringBuffer txt = new StringBuffer();
        String post      = "";
        long mins   = remainder / (60 * 1000);
        long secs   = remainder % (60 * 1000);
        if(mins > 0)
            txt.append(Tools.oh(mins)).append(":");
        else
            post = " seconds";
        txt.append(Tools.oh(secs / 1000)).append(post);
        counter.setText(txt.toString());
    }

    public void handOver(Tandem t, Displayable cur)
    {
        reset();
        moi = cur;
        cur.addCommand(start);
        cur.setCommandListener(this);
    }

    public void reset()
    {
        if(hidden) return;
        if(active)
        {
            moi.removeCommand(bg);
            moi.addCommand(start);
            active = false;
        }
        counter.setText("Press the Start button to begin timing.");
        remainder = duration;
    }
}

class UpdateXMLHandler extends DefaultHandler implements CommandListener
{
    private Restartable midlet;
    private Displayable prev;
    private App describedApp;
    private String upgurl, newVersion;
    private AppForm curform;
    public boolean refresh, isUpgrade, curBold, curItalic, curUnderline;
    private Alarm   alarm;
    private String curId, runChars, curTitle, curQn, curChoice, curVal, curSecs, curLink;
    private Vector helps, choices;

    public UpdateXMLHandler(App a, Restartable r, Displayable d, Alarm alm, boolean ref)
    {
        midlet          = r;
        describedApp    = a;
        prev            = d;
        refresh         = ref;
        isUpgrade       = false;
        alarm           = alm;
        helps           = new Vector(5, 5);
        choices         = new Vector(5, 5);
    }

    public void commandAction(Command c, Displayable d)
    {
        try
        {
            midlet.asMIDlet().platformRequest(upgurl);
        }
        catch(ConnectionNotFoundException cnfe)
        {
            //  cnfe.printStackTrace();
            Alert al = new Alert("Upgrade Failed", "Could not successfully fetch the system upgrade from " + upgurl, null, AlertType.ERROR);
            al.setTimeout(Alert.FOREVER);
            Display.getDisplay(midlet.asMIDlet()).setCurrent(al, prev);
        }
    }

    public void startElement(String uri, String nom, String qnom, Attributes attr) throws SAXException
    {
        Question q = null;
        if(qnom.equalsIgnoreCase("upgrade"))
        {
            upgurl      =   attr.getValue("href");
            isUpgrade   =   true;
            return;
        }
        if(qnom.equalsIgnoreCase("update"))
        {
            newVersion  =   attr.getValue("v");
            if(newVersion != null)
                describedApp.status(newVersion);
        }
        else if(qnom.equalsIgnoreCase("f"))
        {
            helps.removeAllElements();
            String resp =   attr.getValue("r");
            if(resp == null) resp = "yes";
            String prep =   attr.getValue("pre");
            if(prep == null) prep = "";
            curQn   = attr.getValue("id");
            if(curQn == null) curQn = "";
            curform = new AppForm(prev, curQn, prep, !resp.equals("no"));
            describedApp.addForm(curform);
        }
        else if(qnom.equalsIgnoreCase("u"))
        {
            describedApp.addURL(attr.getValue("href"));
        }
        else if(qnom.equalsIgnoreCase("vc"))
        {
            q = new VHTCodeQuestion(midlet, prev);
        }
        else if(qnom.equalsIgnoreCase("pulser"))
        {
            curSecs = attr.getValue("secs");
            if(curSecs == null) curSecs = "60";
            curTitle = attr.getValue("t");
            if(curTitle == null) curTitle = "Counting Timer";
        }
        else if(qnom.equalsIgnoreCase("cd"))
        {
            curSecs = attr.getValue("secs");
            if(curSecs == null) curSecs = "60";
            curTitle = attr.getValue("t");
            if(curTitle == null) curTitle = "Countdown Timer";
        }
        else if(qnom.equalsIgnoreCase("a"))
        {
            curLink = attr.getValue("href");
            if(curLink == null) curLink = "";
        }
        else if(qnom.equalsIgnoreCase("say"))
        {
            String b = attr.getValue("b");
            curBold  = b != null && b.equals("b");
            b = attr.getValue("i");
            curItalic   = b != null && b.equals("i");
            b = attr.getValue("u");
            curUnderline    = b != null && b.equals("u");
        }
        else if(qnom.equalsIgnoreCase("i") || qnom.equalsIgnoreCase("x") || qnom.equalsIgnoreCase("f"))
        {
            curId = attr.getValue("id");
            if(curId == null) curId = "";
        }
        else if(qnom.equalsIgnoreCase("h"))
        {
            curTitle = attr.getValue("t");
            if(curTitle == null) curTitle = "Help";
        }
        else if(qnom.equalsIgnoreCase("o"))
        {
            curVal = attr.getValue("v");
            if(curVal == null) curVal = "";
        }
        else if(qnom.equalsIgnoreCase("ph"))
        {
            curTitle = attr.getValue("t");
            if(curTitle == null) curTitle = "Call";
            runChars = attr.getValue("n");
            if(runChars == null) runChars = "";
            //  helps.addElement(new QuestionAnswerPair("Call " + curTitle, attr.getValue("n"), true));
        }
        else if(qnom.equalsIgnoreCase("choice"))
        {
            curChoice = attr.getValue("t");
            if(curChoice == null) curChoice = "Pick one";
            curId = attr.getValue("id");
            if(curId == null) curId = "";
        }
        if(q != null)
        {
            q.setApp(describedApp);
            q.setForm(curform);
            curform.addElement(q);
        }
    }

    public void endElement(String uri, String nom, String qnom) throws SAXException
    {
        Question q = null;
        if(qnom.equalsIgnoreCase("forms"))
        {
            String updtxt = "Update Questionnaire";
            curform = new AppForm(prev, updtxt, "", false);
            q = new UpdaterQuestion(midlet, prev, describedApp, alarm, updtxt);
            describedApp.addForm(curform);
            describedApp.setUpdater((UpdaterQuestion) q);
        }
        else if(qnom.equalsIgnoreCase("i") || qnom.equalsIgnoreCase("x"))
        {
            q = (qnom.equalsIgnoreCase("i") ? new FewQuestion(midlet, prev, runChars, curId) : new NumberQuestion(midlet, prev, runChars, curId));
        }
        else if(qnom.equalsIgnoreCase("say"))
        {
            q = new SayQuestion(midlet, prev, curBold, curItalic, curUnderline, runChars);
        }
        else if(qnom.equalsIgnoreCase("a"))
        {
            q = new LinkQuestion(midlet, prev, curLink, runChars);
        }
        else if(qnom.equalsIgnoreCase("o"))
        {
            choices.addElement(new QuestionAnswerPair(runChars, curVal));
        }
        else if(qnom.equalsIgnoreCase("date"))
        {
            q = new DateQuestion(midlet, prev, runChars);
        }
        else if(qnom.equalsIgnoreCase("h"))
        {
            helps.addElement(new QuestionAnswerPair(curTitle, runChars));
        }
        else if(qnom.equalsIgnoreCase("ph"))
        {
            helps.addElement(new QuestionAnswerPair("Call " + curTitle, runChars, true));
        }
        else if(qnom.equalsIgnoreCase("pulser"))
        {
            q = new PulserQuestion(midlet, prev, alarm, curTitle, runChars, Long.parseLong(curSecs));
        }
        else if(qnom.equalsIgnoreCase("cd"))
        {
            q = new CountDownQuestion(midlet, prev, alarm, curTitle, runChars, Long.parseLong(curSecs));
        }
        else if(qnom.equalsIgnoreCase("choice"))
        {
            int chsz = choices.size();
            if(chsz > 0)
            {
                ChoiceQuestion chq =   new ChoiceQuestion(midlet, prev, curChoice, curId);
                for(int notI = 0; notI < chsz; ++notI)
                {
                    chq.add((QuestionAnswerPair) choices.elementAt(notI));
                }
                q   =   (Question) chq;
            }
        }
        else if(qnom.equalsIgnoreCase("f"))
        {
            int hsz = helps.size();
            if(hsz > 0)
            {
                HelpQuestion hq =   new HelpQuestion(midlet, prev, curQn);
                for(int notI = 0; notI < hsz; ++notI)
                {
                    hq.add((QuestionAnswerPair) helps.elementAt(notI));
                }
                q   =   (Question) hq;
            }
        }
        if(q != null)
        {
            q.setApp(describedApp);
            q.setForm(curform);
            curform.addElement(q);
        }
    }

    public void characters(char[] ch, int start, int length) throws SAXException
    {
        runChars = new String(ch, start, length);
    }
}

class UpdaterQuestion extends Question implements HasAnswer, Runnable, CommandListener
{
    private StringItem report;
    private Displayable nxt;
    private Tandem tandem;
    private UpdateXMLHandler upx;
    private VHTCodeQuestion vhtCode;
    private Command update;
    private App application;
    private boolean updating;
    
    public UpdaterQuestion(Restartable m, Displayable d, App a, Alarm alm, String text)
    {
        super(m, d);
        report      = new StringItem(text, "");
        upx         = new UpdateXMLHandler(a, midlet, prev, alm, false);
        vhtCode     = new VHTCodeQuestion(m, d);
        update      = new Command("Update", Command.OK, 1);
        application = a;
        updating    = false;
        reset();
    }

    public String validate()
    {
        return vhtCode.validate();
    }
    
    public String tag()
    {
        return "upd";
    }

    public HasAnswer[] fields()
    {
        HasAnswer[] them = {vhtCode, this};
        return them;
    }

    public void reset()
    {
        report.setText("Provide your VHT code and select Update.");
    }

    public void commandAction(Command c, Displayable d)
    {
        if(c == update)
        {
            if(vhtCode.getAnswer().length() < 1)
            {
                Alert al = new Alert("VHT Code", "Please provide a VHT code.", null, AlertType.ERROR);
                Display.getDisplay(midlet.asMIDlet()).setCurrent(al, d);
                return;
            }
            if(updating) return;
            updating = true;
            mother.displayForm().removeCommand(update);
            Thread th = new Thread(this);
            th.start();
        }
        else
        {
            Display.getDisplay(midlet.asMIDlet()).setCurrent(prev);
        }
    }

    public Item getField()
    {
        return report;
    }

    public String getAnswer()
    {
        return "";
    }

    private boolean recordWhatWeHave(byte[] them)
    {
        String stuff = new String(them);

        if(stuff.substring(0, 2).equals("OK"))
            return true;
        try
        {
            SAXParserFactory spf = SAXParserFactory.newInstance();
            SAXParser parser     = spf.newSAXParser();
            parser.parse(new ByteArrayInputStream(them), upx);
        }
        catch(Exception e)
        {
            //  e.printStackTrace();
        }
        if(upx.isUpgrade)
        {
            Alert upgal = new Alert("Updates!", "A new version of the application is available. Please accept the update.", null, AlertType.INFO);
            upgal.setTimeout(Alert.FOREVER);
            upgal.setCommandListener(upx);
            Display.getDisplay(midlet.asMIDlet()).setCurrent(upgal, prev);
        }
        else
        {
            try
            {
                RecordStore recs = RecordStore.openRecordStore("descr", true);
                try
                {
                    recs.setRecord(1, them, 0, them.length);
                }
                catch(InvalidRecordIDException irie)
                {
                    //  irie.printStackTrace();
                    recs.addRecord(them, 0, them.length);
                }
                recs.closeRecordStore();
            }
            catch(RecordStoreException rse)
            {
                //  rse.printStackTrace();
            }
        }
        return false;
    }

    public void handOver(Tandem t, Displayable cur)
    {
        nxt       = cur;
        tandem    = t;
        mother.displayForm().addCommand(update);
        cur.setCommandListener(this);
        reset();
    }

    public void run()
    {
        String[] urls = {"http://localhost:3000",
                         "http://inscale.herokuapp.com",
                         "http://inscale.malariaconsortium.org",
                         "http://inscale.malariaconsortium.org:3000"};
        int wins          = urls.length;
        byte[] dest       = null;
        String gotVersion = application.version();
        StringBuffer urlList    = new StringBuffer();
        String gotStatus        = application.status();
        for(int notI = 0; notI < urls.length; ++notI)
        {
            try
            {
                String realU    = urls[notI].concat("/system/get_latest/inscale/" + gotVersion + "/" + gotStatus + "?vht=" + vhtCode.getAnswer());
                report.setText("Trying " + realU + " ...");
                urlList.append(realU);
                HttpConnection htc = (HttpConnection) Connector.open(realU);
                InputStream str    = htc.openInputStream();
                dest               = new byte[(int) htc.getLength()];
                str.read(dest);
                str.close();
                htc.close();
                if(recordWhatWeHave(dest))
                {
                    Alert al = new Alert("Up-to-date", "Everything you have is up-to-date.", null, AlertType.CONFIRMATION);
                    al.setTimeout(Alert.FOREVER);
                    Display.getDisplay(midlet.asMIDlet()).setCurrent(al, prev);
                    break;
                }
                else
                {
                    if(! upx.isUpgrade)
                    {
                        //  TODO: Review this. There is thrown an error due to an addCommand that follows it.
                        midlet.restart();
                    }
                }
                report.setText(report.getText() + " ... succeeded!\n\n[" + new String(dest) + "]");
//                Alert al = new Alert("Updated", "You now have a new questionnaire version: " + application.status(), null, AlertType.CONFIRMATION);
//                al.setTimeout(Alert.FOREVER);
//                Display.getDisplay(midlet.asMIDlet()).setCurrent(al, prev);
                break;
            }
            catch(IOException e)
            {
                urlList.append("\n\n");
                --wins;
            }
        }
        if(wins < 1)
        {
            String tt = "Updates Failed", bt = Integer.toString(urls.length) + " failed attempts to update from " + Integer.toString(urls.length) + " different URLs.\n\n" + urlList;
            report.setLabel(tt);
            report.setText(bt);
            Alert al = new Alert(tt, bt, null, AlertType.ERROR);
            al.setTimeout(Alert.FOREVER);
            Display.getDisplay(midlet.asMIDlet()).setCurrent(al, prev);
        }
        mother.displayForm().addCommand(update);
        updating = false;
    }
}

class TimestampQuestion extends Question implements Tandem, HasAnswer
{
    public TimestampQuestion(Restartable m, Displayable d)
    {
        super(m, d);
    }

    public String validate()
    {
        return null;
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
        return Long.toString(new Date().getTime(), 16);
    }

    public String tag()
    {
        return "t";
    }

    public void handOver(Tandem t, Displayable d)
    {
        
    }

    public void reset()
    {
        
    }
}

class VHTCodeQuestion extends Question implements Tandem, HasAnswer
{
    private TextField vent;

    public VHTCodeQuestion(Restartable m, Displayable d)
    {
        super(m, d);
        vent = new TextField("VHT Code", "", 4, TextField.NUMERIC);
    }

    public String validate()
    {
        if(vent.getString().length() < 1)
            return "Please enter your VHT code.";
        return null;
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

    }

    public void reset()
    {
        
    }
}

class DateQuestion extends Question implements Tandem, HasAnswer
{
    private DateField dateField;
    private String title;
    
    public DateQuestion(Restartable m, Displayable d, String t)
    {
        super(m, d);
        title       = t;
        dateField   =   new DateField(title, DateField.DATE);
    }

    public String validate()
    {
        if(dateField.getDate() == null)
            return "Pick a date for " + title;
        return null;
    }

    public HasAnswer[] fields()
    {
        HasAnswer[] them = {this};
        return them;
    }

    public void handOver(Tandem t, Displayable d)
    {

    }

    public void reset()
    {
        
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
        return Long.toString(dateField.getDate().getTime() / 1000, 16);
    }
}

class PendingMessages extends DefaultHandler
{
    private Stack already;
    private App   application;
    private String prepend;
    private Submission cursub;
    private String tag, val, ver;

    public PendingMessages(Stack d, App a)
    {
        already     =   d;
        application =   a;
        prepend     = null;
        cursub      = null;
    }

    public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException 
    {
        if(qName.equalsIgnoreCase("v"))
        {
            tag = attributes.getValue("t");
        }
        else if(qName.equalsIgnoreCase("sub"))
        {
            ver = attributes.getValue("v");
            if(ver == null) ver = "unspecified";
        }
    }

    public void endElement(String uri, String localName, String qName) throws SAXException
    {
        if(qName.equalsIgnoreCase("sub"))
        {
            cursub.submitterVersion(ver);
            already.addElement(cursub);
            cursub = null;
        }
        else if(qName.equalsIgnoreCase("v"))
        {
            cursub.put(tag, val);
            tag = val = null;
        }
    }

    public void characters(char[] chs, int s, int l) throws SAXException
    {
        if(cursub == null)
        {
            prepend = new String(chs, s, l);
            Vector forms = application.getForms();
            for(int notI = 0; notI < forms.size(); ++notI)
            {
                AppForm af = (AppForm) forms.elementAt(notI);
                if(! af.responds)
                    continue;
                if(af.prepend().equals(prepend))
                    cursub  = new Submission(af);
            }
        }
        else
        {
            val = new String(chs, s, l);
        }
    }
}

class MeekList extends List implements Tandem
{
    MIDlet mama;

    public MeekList(MIDlet m, String t, int x)
    {
        super(t, x);
        mama = m;
    }

    public void handOver(Tandem t, Displayable d)
    {
        Display.getDisplay(mama).setCurrent(this);
    }
}

public class inSCALE extends MIDlet implements CommandListener, Restartable
{
    private String appdescr;
    private MeekList mainMenu;
    private Command pending, quitbut, /*formChoice,*/ version;
    private Stack already;
    private App application;
    private SendPending sender;
    private boolean onceBefore;

    private boolean getWhatWeHave()
    {
        try
        {
            RecordStore rs = RecordStore.openRecordStore("descr", true);
            byte[] ans     = rs.getRecord(1);
            rs.closeRecordStore();
            if(ans != null)
            {
                appdescr = new String(ans);
                return true;
            }
        }
        catch(RecordStoreException e)
        {
            //  e.printStackTrace();
        }
        try
        {
            InputStream ins = getClass().getResourceAsStream("/appdescription.xml");
            byte[] newdescr = new byte[ins.available()];
            ins.read(newdescr);
            ins.close();
            appdescr = new String(newdescr);
        }
        catch(Exception e)
        {
            //  e.printStackTrace();
            appdescr = "<app>\n    <urls>\n        <u href=\"http://localhost:3000/record/\" />\n        <u href=\"http://inscale.malariaconsortium.org/record/\" />\n        <u href=\"http://inscale.malariaconsortium.org/record/\" />\n        <u href=\"http://inscale.malariaconsortium.org:3000/record/\" />\n        <u href=\"http://inscale.malariaconsortium.org:3000/record/\" />\n        <u href=\"sms://+8779\" />\n    </urls>\n    <forms>\n        <f id=\"Weekly Report\" pre=\"vht \">\n            <t />\n            <vc />\n            <date>Start date:</date>\n            <few>\n                <i id=\"male\">Number of male children (SEX M):</i>\n                <i id=\"fem\">Number of female children (SEX F):</i>\n                <i id=\"rdtp\">Number of RDT results positive (+):</i>\n                <i id=\"rdtn\">Number of RDT results negative (-):</i>\n                <i id=\"diar\">Number of children with diarrhoea:</i>\n                <i id=\"fastb\">Number of children with fast breathing:</i>\n                <i id=\"fever\">Number of children with fever:</i>\n                <i id=\"danger\">Number of children with danger sign:</i>\n                <i id=\"treated\">Number of children treated within 24 hours:</i>\n                <i id=\"ors\">Number treated with ORS:</i>\n                <i id=\"zinc12\">Number treated with Zinc 1/2 tablet:</i>\n                <i id=\"zinc\">Number treated with Zinc 1 tablet:</i>\n                <i id=\"amoxr\">Number treated with Amoxicillin Red:</i>\n                <i id=\"amoxg\">Number treated with Amoxicillin Green:</i>\n                <i id=\"coary\">Number treated with ACT - Coartem Yellow:</i>\n                <i id=\"coarb\">Number treated with ACT - Coartem Blue:</i>\n                <i id=\"recart\">Number treated with Rectal Artesunate (total):</i>\n                <i id=\"ref\">Number of children referred:</i>\n                <i id=\"death\">Number of children who died:</i>\n                <i id=\"mnew\">Number of male newborns (SEX M):</i>\n                <i id=\"fnew\">Number of female newborns (SEX F):</i>\n                <i id=\"hv1\">Number of home visits Day 1:</i>\n                <i id=\"hv3\">Number of home visits Day 3:</i>\n                <i id=\"hv7\">Number of home visits Day 7:</i>\n                <i id=\"newbdanger\">Number of newborns with danger signs:</i>\n                <i id=\"newbref\">Number of newborns referred:</i>\n                <i id=\"yellow\">Number of children with Yellow MUAC:</i>\n                <i id=\"red\">Number of children with Red MUAC/Oedema:</i>\n            </few>\n            <int>\n                <x id=\"recartbal\">Rectal Artesunate balance:</x>\n                <x id=\"orsbal\">ORS balance:</x>\n                <x id=\"zincbal\">Zinc balance:</x>\n                <x id=\"yactbal\">Yellow ACT balance:</x>\n                <x id=\"bactbal\">Blue ACT balance:</x>\n                <x id=\"ramoxbal\">Red Amoxicillin balance:</x>\n                <x id=\"gamoxbal\">Green Amoxicillin balance:</x>\n                <x id=\"rdtbal\">RDT balance:</x>\n            </int>\n            <choice t=\"Pairs of gloves balance\" id=\"glvbal\">\n                <o v=\"MT5\">5 pairs or more</o>\n                <o v=\"LT5\">Less than 5 pairs</o>\n            </choice>\n        </f>\n        <f id=\"Respiratory Timer\" r=\"no\">\n            <pulser secs=\"60\" t=\"Respiratory Timer\">Please note the number of breaths per minute.</pulser>\n        </f>\n        <f id=\"RDT Timer\" r=\"no\">\n            <cd secs=\"900\" t=\"RDT Countdown Timer\">15 minutes have elapsed. Please check and record the results.</cd>\n        </f>\n    </forms>\n</app>";
        }
        return false;
    }

    private void loadPending()
    {
        try
        {
            RecordStore rs = RecordStore.openRecordStore("pending", true);
            byte[] pends   = rs.getRecord(1);
            rs.closeRecordStore();
            if(pends == null)
                return;
            SAXParserFactory spf = SAXParserFactory.newInstance();
            SAXParser parser     = spf.newSAXParser();
            PendingMessages pnd  = new PendingMessages(already, application);
            parser.parse(new ByteArrayInputStream(pends), pnd);
        }
        catch(Exception e)
        {
            //  e.printStackTrace();
        }
    }

    private String loadDescription()
    {
        if(getWhatWeHave())
        {
            //  We are using from memory.
        }
        return appdescr;
    }

    private void paint(List l, Vector fs)
    {
        for(int notI = 0; notI < fs.size(); ++notI)
        {
            AppForm fm = (AppForm) fs.elementAt(notI);
            l.append(fm.id(), null);
        }
    }

    public void startApp()
    {
        mainMenu        = new MeekList(this, "inSCALE", Choice.EXCLUSIVE | Choice.IMPLICIT | Choice.TEXT_WRAP_OFF);
        pending         = new Command("Pending", Command.OK, 4);
        quitbut         = new Command("Quit", Command.EXIT, 0);
        //  formChoice      = new Command("Run", Command.OK, 4);
        version         = new Command("Version Details", Command.HELP, 1);
        application     = new App(this);
        sender          = new SendPending(mainMenu, this, application);
        already         = new Stack();
        onceBefore      = false;
        application.setSender(sender);
        this.loadPending();
        mainMenu.addCommand(pending);
        mainMenu.addCommand(quitbut);
        mainMenu.addCommand(version);
        mainMenu.setCommandListener(this);
        restart();
    }

    public MIDlet asMIDlet()
    {
        return this;
    }

    public void restart()
    {
        if(onceBefore)
        {
            Alert quitter = new Alert("Restart", "The recent update (" + application.status() + ") requires a restart.", null, AlertType.INFO);
            quitter.setTimeout(Alert.FOREVER);
            final MIDlet me = this;
            quitter.setCommandListener(new CommandListener()
            {
                public void commandAction(Command c, Displayable d)
                {
                    me.notifyDestroyed();
                }
            });
            Display.getDisplay(me).setCurrent(quitter, mainMenu);
            return;
        }
        mainMenu.deleteAll();
        String descr = this.loadDescription();
        application.initialiseForms(descr, already, this, mainMenu);
        paint(mainMenu, application.getForms());
        mainMenu.setSelectedIndex(0, true);
        Display.getDisplay(this).setCurrent(mainMenu);
        onceBefore = true;
    }

    public void commandAction(Command c, Displayable d)
    {
        if(c == quitbut)
        {
            this.notifyDestroyed();
        }
        else if(c == pending)
        {
            sender.handOver(mainMenu, d);
            Alert alt   =   sender.informationAlert();
            if(sender.sendPending())
            {
                Display.getDisplay(this).setCurrent(alt, sender.sendingProcess(d));
            }
            else
            {
                Display.getDisplay(this).setCurrent(alt, d);
            }
        }
        else if(c == version)
        {
            String qstat = application.status(),
                   vers  = /*   application.version() */ application.date();
            Alert al = new Alert("Version and Status", "App version: 2.0 (" + vers + ")\nQuestionnaire status: " + qstat, null, AlertType.INFO);
            al.setTimeout(Alert.FOREVER);
            Display.getDisplay(this).setCurrent(al);
        }
        else if(application.getForms().size() > 0 && c == List.SELECT_COMMAND)
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
