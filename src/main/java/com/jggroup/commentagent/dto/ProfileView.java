package com.jggroup.commentagent.dto;

/** 대시보드 표시용 프로필 상태 — 이름 + 유튜브(구글) 로그인 여부. */
public record ProfileView(String name, boolean loggedIn) {
}
