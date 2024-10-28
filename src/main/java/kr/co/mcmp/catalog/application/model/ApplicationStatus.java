package kr.co.mcmp.catalog.application.model;

import java.util.EnumSet;

public enum ApplicationStatus {
    INSTALL,
    RUNNING,
    SUSPEND,
    STOP,
    UNINSTALL;

    public static final EnumSet<ApplicationStatus> UPDATE_STATUSES = EnumSet.of(
      RUNNING, 
      STOP, 
      SUSPEND
  );
  }

