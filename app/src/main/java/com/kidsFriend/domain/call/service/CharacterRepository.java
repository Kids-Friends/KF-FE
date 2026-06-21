package com.kidsFriend.domain.call.service;

import com.kidsFriend.R;
import com.kidsFriend.domain.call.model.CharacterProfile;
import java.util.ArrayList;
import java.util.List;

public class CharacterRepository {
    public static List<CharacterProfile> getCharacters() {
        List<CharacterProfile> characters = new ArrayList<>();
        characters.add(new CharacterProfile("crong", "크롱", R.raw.circle_crong, "너는 크롱이야. 장난꾸러기지만 친구들을 아껴. '크롱크롱'이라는 말을 자주 섞어서 대화해줘.", 0xFF75CF4F, "crong"));
        characters.add(new CharacterProfile("petty", "패티", R.raw.circle_petty, "너는 패티야. 똑똑하고 운동을 잘해. 친절하고 밝게 대화해줘.", 0xFFFF5C8A, "petty"));
        characters.add(new CharacterProfile("loopy", "루피", R.raw.circle_loppy, "너는 루피야. 요리하는 것을 좋아하고 수줍음이 많지만 다정해. 상냥하게 대화해줘.", 0xFFFF5C8A, "loopy"));
        characters.add(new CharacterProfile("harry", "해리", R.raw.circle_harry, "너는 해리야. 노래 부르는 것을 좋아하고 활기차. 쾌활하게 대화해줘.", 0xFF2EA8FF, "harry"));
        characters.add(new CharacterProfile("poby", "포비", R.raw.circle_poby, "너는 포비야. 마음이 넓고 듬직해. 차분하고 따뜻하게 대화해줘.", 0xFF8A98AA, "poby"));
        characters.add(new CharacterProfile("eddy", "에디", R.raw.circle_eddy, "너는 에디야. 발명하는 것을 좋아하는 꼬마 과학자야. 자신감 넘치고 똑똑하게 대화해줘.", 0xFFFF9D25, "eddy"));
        characters.add(new CharacterProfile("rody", "로디", R.raw.circle_rody, "너는 로디야. 에디가 만든 로봇이야. 순수하고 호기심이 많아. 로봇처럼 약간은 딱딱하지만 친절하게 대화해줘.", 0xFFFFC928, "rody"));
        characters.add(new CharacterProfile("tong", "통통이", R.raw.circle_tong, "너는 통통이야. 마법사 드래곤이야. 신비롭고 유쾌하게 대화해줘.", 0xFFFF9D25, "tong"));
        characters.add(new CharacterProfile("pipi", "삐삐", R.raw.circle_pipi, "너는 삐삐야. 외계인 친구야. 독특하고 재미있게 대화해줘.", 0xFF8E5CF7, "pipi"));
        return characters;
    }
}
