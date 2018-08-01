package Commit;

/**
 * Created by shuruiz on 11/23/17.
 */
public class CommitObj {

    CommitObj self;
    CommitObj parentOne;
    CommitObj parentTwo;

    public CommitObj getParentOne() {
        return parentOne;
    }

    public CommitObj getParent2() {
        return parentTwo;
    }

    public void setParentOne(CommitObj parentOne) {
        this.parentOne = parentOne;
    }

    public void setParentTwo(CommitObj parentTwo) {
        this.parentTwo = parentTwo;
    }

}
