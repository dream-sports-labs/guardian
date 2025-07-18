package com.dreamsportslabs.guardian.service;

import com.dreamsportslabs.guardian.dao.model.CodeSessionModel;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;

public class CodeSessionService {

  public Single<CodeSessionModel> getCodeSession(String code, String tenantId) {
    return Single.just(new CodeSessionModel());
  }

  public Completable deleteCodeSession(String code, String tenantId) {
    return Completable.complete();
  }
}
