/**
 * Created by shuruiz on 11/23/17.
 */
public class Commit {

    Commit self;
    Commit parentOne;
    Commit parentTwo;

    public Commit getParentOne() {
        return parentOne;
    }

    public Commit getParent2() {
        return parentTwo;
    }

    public void setParentOne(Commit parentOne) {
        this.parentOne = parentOne;
    }

    public void setParentTwo(Commit parentTwo) {
        this.parentTwo = parentTwo;
    }

}
