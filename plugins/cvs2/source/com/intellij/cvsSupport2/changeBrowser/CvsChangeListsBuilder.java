package com.intellij.cvsSupport2.changeBrowser;

import com.intellij.cvsSupport2.connections.CvsEnvironment;
import com.intellij.cvsSupport2.history.CvsRevisionNumber;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.netbeans.lib.cvsclient.command.log.Revision;
import org.netbeans.lib.cvsclient.command.log.SymbolicName;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class CvsChangeListsBuilder {
  private static class ChangeListKey {
    public String branch;
    public String author;
    public String message;

    public ChangeListKey(final String branch, final String author, final String message) {
      this.branch = branch;
      this.author = author;
      this.message = message;
    }

    public boolean equals(final Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      final ChangeListKey that = (ChangeListKey)o;

      if (!author.equals(that.author)) return false;
      if (branch != null ? !branch.equals(that.branch) : that.branch != null) return false;
      if (!message.equals(that.message)) return false;

      return true;
    }

    public int hashCode() {
      int result;
      result = (branch != null ? branch.hashCode() : 0);
      result = 31 * result + author.hashCode();
      result = 31 * result + message.hashCode();
      return result;
    }
  }

  private final Map<ChangeListKey, List<CvsChangeList>> myCache = new HashMap<ChangeListKey, List<CvsChangeList>>();

  private long myLastNumber = 0;
  private final String myRootPath;
  private final CvsEnvironment myEnvironment;
  private final Project myProject;
  private final VirtualFile myRootFile;

  public CvsChangeListsBuilder(final String rootPath, final CvsEnvironment environment, final Project project,
                               final VirtualFile rootFile) {
    myRootPath = rootPath;
    myEnvironment = environment;
    myProject = project;
    myRootFile = rootFile;
  }

  public List<CvsChangeList> getVersions() {
    final ArrayList<CvsChangeList> result = new ArrayList<CvsChangeList>();
    for (List<CvsChangeList> versions : myCache.values()) {
      result.addAll(versions);
    }
    return result;
  }

  private void addRevision(RevisionWrapper revision) {
    final Revision cvsRevision = revision.getRevision();
    CvsChangeList version = findOrCreateVersionFor(cvsRevision.getMessage(),
                                                   revision.getTime(),
                                                   cvsRevision.getAuthor(),
                                                   revision.getBranch());

    version.addFileRevision(revision);
  }

  private CvsChangeList findOrCreateVersionFor(final String message, final long date, final String author, final String branch) {
    final ChangeListKey key = new ChangeListKey(branch, author, message);
    final List<CvsChangeList> versions = myCache.get(key);
    if (versions != null) {
      final CvsChangeList lastVersion = versions.get(versions.size() - 1);
      if (lastVersion.containsDate(date)) {
        return lastVersion;
      }
    }

    final CvsChangeList result = new CvsChangeList(myProject, myEnvironment, myRootFile,
                                                   myLastNumber, message, date, author, myRootPath);
    myLastNumber += 1;

    if (!myCache.containsKey(key)) {
      myCache.put(key, new ArrayList<CvsChangeList>());
    }

    myCache.get(key).add(result);
    return result;
  }

  public void addLogs(final List<LogInformationWrapper> logs) {
    List<RevisionWrapper> revisionWrappers = new ArrayList<RevisionWrapper>();

    for (LogInformationWrapper log : logs) {
      final String file = log.getFile();
      if (CvsChangeList.isAncestor(myRootPath, file)) {
        for (Revision revision : log.getRevisions()) {
          if (revision != null) {
            String branchName = getBranchName(revision, log.getSymbolicNames());
            revisionWrappers.add(new RevisionWrapper(file, revision, branchName));
          }
        }
      }
    }

    Collections.sort(revisionWrappers);


    for (RevisionWrapper revisionWrapper : revisionWrappers) {
      addRevision(revisionWrapper);
    }
  }

  @Nullable
  private static String getBranchName(final Revision revision, final List<SymbolicName> symbolicNames) {
    CvsRevisionNumber number = new CvsRevisionNumber(revision.getNumber().trim());
    final int[] subRevisions = number.getSubRevisions();
    String branchName = null;
    if (subRevisions != null && subRevisions.length == 4) {
      int branchRevNumber = subRevisions [2];
      CvsRevisionNumber branchNumber = number.removeTailVersions(2).addTailVersions(0, branchRevNumber);
      String branchNumberString = branchNumber.asString();
      for(SymbolicName name: symbolicNames) {
        if (name.getRevision().equals(branchNumberString)) {
          branchName = name.getName();
          break;
        }
      }
    }
    return branchName;
  }
}
