using System;
using System.Text;
using System.Collections.Generic;
using System.Runtime.InteropServices;

public static class NeutronInstallerUi {
  public sealed class Control {
    public long Handle; public int Id; public string Text; public string ClassName; public bool Enabled; public bool Visible;
  }
  public sealed class Window {
    public long Handle; public int Pid; public string Title; public bool Visible; public bool Enabled;
    public List<Control> Controls = new List<Control>();
  }
  delegate bool EnumProc(IntPtr hwnd, IntPtr parameter);
  [DllImport("user32.dll")] static extern bool EnumWindows(EnumProc callback, IntPtr parameter);
  [DllImport("user32.dll")] static extern bool EnumChildWindows(IntPtr parent, EnumProc callback, IntPtr parameter);
  [DllImport("user32.dll")] static extern uint GetWindowThreadProcessId(IntPtr hwnd, out uint pid);
  [DllImport("user32.dll", CharSet=CharSet.Unicode)] static extern int GetWindowText(IntPtr hwnd, StringBuilder text, int length);
  [DllImport("user32.dll", CharSet=CharSet.Unicode)] static extern int GetClassName(IntPtr hwnd, StringBuilder text, int length);
  [DllImport("user32.dll")] static extern int GetDlgCtrlID(IntPtr hwnd);
  [DllImport("user32.dll")] static extern IntPtr GetParent(IntPtr hwnd);
  [DllImport("user32.dll")] static extern bool IsWindowVisible(IntPtr hwnd);
  [DllImport("user32.dll")] static extern bool IsWindowEnabled(IntPtr hwnd);
  [DllImport("user32.dll", EntryPoint="PostMessageW", SetLastError=true)] static extern bool PostMessage(IntPtr hwnd,uint message,IntPtr wparam,IntPtr lparam);
  [DllImport("user32.dll", EntryPoint="SendMessageTimeoutW",CharSet=CharSet.Unicode)] static extern IntPtr ReadTextMessage(IntPtr hwnd,uint message,IntPtr wparam,StringBuilder text,uint flags,uint timeout,out IntPtr result);
  static string ReadText(IntPtr hwnd) { var text = new StringBuilder(4096); IntPtr result; ReadTextMessage(hwnd,0x000D,new IntPtr(text.Capacity),text,2,200,out result); return text.ToString(); }
  static string ReadClass(IntPtr hwnd) { var text = new StringBuilder(256); GetClassName(hwnd,text,text.Capacity); return text.ToString(); }
  public static Window[] Read(int[] pids) {
    var selected = new HashSet<int>(pids); var windows = new List<Window>();
    EnumWindows(delegate(IntPtr hwnd,IntPtr unused) {
      uint pid; GetWindowThreadProcessId(hwnd,out pid); if(!selected.Contains((int)pid)) return true;
      var window = new Window { Handle=hwnd.ToInt64(),Pid=(int)pid,Title=ReadText(hwnd),Visible=IsWindowVisible(hwnd),Enabled=IsWindowEnabled(hwnd) };
      EnumChildWindows(hwnd,delegate(IntPtr child,IntPtr ignored) {
        window.Controls.Add(new Control { Handle=child.ToInt64(),Id=GetDlgCtrlID(child),Text=ReadText(child),ClassName=ReadClass(child),Enabled=IsWindowEnabled(child),Visible=IsWindowVisible(child) }); return true;
      },IntPtr.Zero); windows.Add(window); return true;
    },IntPtr.Zero); return windows.ToArray();
  }
  public static void Click(long handle,int expectedPid) {
    uint pid, parentPid; var hwnd = new IntPtr(handle); GetWindowThreadProcessId(hwnd,out pid);
    var parent = GetParent(hwnd); GetWindowThreadProcessId(parent,out parentPid);
    int id = GetDlgCtrlID(hwnd);
    if(expectedPid<=0 || hwnd==IntPtr.Zero || parent==IntPtr.Zero || pid!=(uint)expectedPid || parentPid!=(uint)expectedPid ||
       ReadClass(hwnd)!="Button" || (id!=1 && id!=2) || !IsWindowEnabled(hwnd) || !IsWindowVisible(hwnd) ||
       !IsWindowEnabled(parent) || !IsWindowVisible(parent))
      throw new InvalidOperationException("Refusing an unowned, hidden, disabled or unexpected installer button/parent.");
    // A synchronous BM_CLICK can remain on the stack while nsDialogs::Show
    // runs its nested custom-page loop. Queue the same BN_CLICKED notification
    // to the real button parent instead; NSIS still executes every normal
    // page-leave/preflight handler. Posting is not evidence of page completion.
    // https://nsis.sourceforge.io/Docs/nsDialogs/Readme.html#step
    // https://learn.microsoft.com/en-us/windows/win32/controls/bn-clicked
    if(!PostMessage(parent,0x0111,new IntPtr(id),hwnd))
      throw new System.ComponentModel.Win32Exception(Marshal.GetLastWin32Error(),"Could not post the owned installer button notification.");
  }
}
