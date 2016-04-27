// Copyright (C) 2013 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.gerrit.acceptance.rest.change;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.Iterables;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.NoHttpd;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.acceptance.PushOneCommit.Result;
import com.google.gerrit.acceptance.TestAccount;
import com.google.gerrit.extensions.api.changes.DraftInput;
import com.google.gerrit.extensions.client.ChangeStatus;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.server.config.AllUsersName;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.inject.Inject;

import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.junit.Test;

@NoHttpd
public class DeleteDraftPatchSetIT extends AbstractDaemonTest {

  @Inject
  private AllUsersName allUsers;

  @Test
  public void deletePatchSetNotDraft() throws Exception {
    String changeId = createChange().getChangeId();
    PatchSet ps = getCurrentPatchSet(changeId);
    String triplet = project.get() + "~master~" + changeId;
    ChangeInfo c = get(triplet);
    assertThat(c.id).isEqualTo(triplet);
    assertThat(c.status).isEqualTo(ChangeStatus.NEW);

    exception.expect(ResourceConflictException.class);
    exception.expectMessage("Patch set is not a draft");
    setApiUser(admin);
    deletePatchSet(changeId, ps);
  }

  @Test
  public void deleteDraftPatchSetNoACL() throws Exception {
    String changeId = createDraftChangeWith2PS();
    PatchSet ps = getCurrentPatchSet(changeId);
    String triplet = project.get() + "~master~" + changeId;
    ChangeInfo c = get(triplet);
    assertThat(c.id).isEqualTo(triplet);
    assertThat(c.status).isEqualTo(ChangeStatus.DRAFT);

    exception.expect(ResourceNotFoundException.class);
    exception.expectMessage("Not found: " + changeId);
    setApiUser(user);
    deletePatchSet(changeId, ps);
  }

  @Test
  public void deleteDraftPatchSetAndChange() throws Exception {
    String changeId = createDraftChangeWith2PS();
    PatchSet ps = getCurrentPatchSet(changeId);

    DraftInput din = new DraftInput();
    din.path = "a.txt";
    din.message = "comment on a.txt";
    gApi.changes().id(changeId).current().createDraft(din);

    if (notesMigration.writeChanges()) {
      assertThat(getDraftRef(admin, ps.getId().getParentKey())).isNotNull();
    }

    ChangeData cd = getChange(changeId);
    assertThat(cd.patchSets()).hasSize(2);
    assertThat(cd.change().currentPatchSetId().get()).isEqualTo(2);
    assertThat(cd.change().getStatus()).isEqualTo(Change.Status.DRAFT);
    deletePatchSet(changeId, ps);

    cd = getChange(changeId);
    assertThat(cd.patchSets()).hasSize(1);
    assertThat(cd.change().currentPatchSetId().get()).isEqualTo(1);

    ps = getCurrentPatchSet(changeId);
    deletePatchSet(changeId, ps);
    assertThat(queryProvider.get().byKeyPrefix(changeId)).isEmpty();

    if (notesMigration.writeChanges()) {
      assertThat(getDraftRef(admin, ps.getId().getParentKey())).isNull();
    }

    exception.expect(ResourceNotFoundException.class);
    gApi.changes().id(ps.getId().getParentKey().get());
  }

  private Ref getDraftRef(TestAccount account, Change.Id changeId)
      throws Exception {
    try (Repository repo = repoManager.openRepository(allUsers)) {
      return repo.exactRef(RefNames.refsDraftComments(account.id, changeId));
    }
  }

  private String createDraftChangeWith2PS() throws Exception {
    PushOneCommit push = pushFactory.create(db, admin.getIdent(), testRepo);
    Result result = push.to("refs/drafts/master");
    push = pushFactory.create(db, admin.getIdent(), testRepo, PushOneCommit.SUBJECT,
        "b.txt", "4711", result.getChangeId());
    return push.to("refs/drafts/master").getChangeId();
  }

  private PatchSet getCurrentPatchSet(String changeId) throws Exception {
    return getChange(changeId).currentPatchSet();
  }

  private ChangeData getChange(String changeId) throws Exception {
    return Iterables.getOnlyElement(queryProvider.get().byKeyPrefix(changeId));
  }

  private void deletePatchSet(String changeId, PatchSet ps) throws Exception {
    gApi.changes().id(changeId).revision(ps.getId().get()).delete();
  }
}
