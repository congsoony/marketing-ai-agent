package com.jggroup.commentagent.dto;

/**
 * 대시보드 표시용 프로필 상태.
 *  - name: 설정상 프로필 이름(유튜브1 등)
 *  - loggedIn: 유튜브(구글) 로그인 여부
 *  - account: 로그인된 구글 계정 표시 이름(없으면 null)
 *  - email: 로그인된 구글 계정 이메일(없으면 null)
 */
public record ProfileView(String name, boolean loggedIn, String account, String email) {
}
