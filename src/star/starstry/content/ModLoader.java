package star.starstry.content;

import mindustry.content.SectorPresets;
import mindustry.ctype.ContentList;

public class ModLoader implements ContentList {
    private final ContentList[] contents = new ContentList[]{
            new StItems(),
            new StBlocks()
    };
    public void load(){
        for(ContentList list : contents){
            list.load();
        }
    }
}
