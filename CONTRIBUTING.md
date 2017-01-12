# Contribution Guidelines for MMT

(if you're looking for the TL;DR, there is one at the bottom)

## Introduction -- The MMT contribution system

Inside MMT we have two main branches and use a [fork and pull](https://help.github.com/articles/about-collaborative-development-models/) based model of collaboration. The two main branches are:

- master -- This branch contains a compilable version of MMT -- but no guarantees beyond that. To achieve this, we use [Travis Tests](https://travis-ci.org/UniFormal/MMT) that compile MMT and also do some minimal testing. Additionally we only allow pull requests (and no direct pushes).
- stable -- Similar to master this should contain a compilable version of MMT. On top of this, it is manually checked for stability, that is someone says "yes, this is a good version of MMT that actually works as intended". We again use the Travis Tests as well as [reviews](https://help.github.com/articles/about-pull-request-reviews/).

## Working on MMT -- working with forks

To make changes to MMT is neccessary to make a [fork of the repository](https://help.github.com/articles/about-forks/) and then create a pull request to merge the changes back into the main branch. A fork is a copy of the repository that can be used to make a changes without affecting the original code. Pull requests should only be made after the actual code compiles and should always go onto the master branch.

### Creating a fork

To create a fork go to the repository page and hit the [Fork](https://github.com/Uniformal/MMT/fork) button. Then select the account you want to fork into. This should usually be your private account and not another organisation. Afterwards you should then be able to clone the forked repository onto your hard drive by typing:

```bash
git clone git@github.com:your-username-here/MMT.git
```

Whenever you now make changes to your local git repository and push or pull them they will now only go to and from your fork -- that means you can experiment and make breaking changes at any time without affecting the main development of MMT.

### Syncing changes with MMT

Whenever some changes are made inside of MMT they will **not** automatically be synced to your fork. To get the latest changes you should [sync the fork](https://help.github.com/articles/syncing-a-fork). To configure this you will first have to add a

```bash
git remote add upstream https://github.com/Uniformal/MMT.git
```

To then get the changes from the so-called upstream repository (that is, the main MMT repository) and merge them into your local master branch you can then:

```bash
# Get all the latest changes
git fetch upstream

# And merge them into your local branch
# You might have to resolve merge conflicts here
git merge upstream/master
```

Finally, you want to push these changes back into your fork:
```bash
git push
```

It is also possible to directly merge changes directly from another fork -- for example the fork of a different repository. To do this you need to add a seperate remote and then fetch and merge from that repository:

```bash
# add a remote to their repository
git remote add someotheruser https://github.com/some-other-user/MMT.git

# fetch and merge their changes
git fetch someotheruser
git merge someotheruser/master
```

Also note that "master" in the ```git merge``` command refers to the master branch of the alternate repository.

### Contributing back to MMT -- creating a pull request and updating the master branch
Once you have made your commits and have locally checked that MMT compiles properly, you are ready to contribute your changes back into the original repository. To do so, you need to create a pull request.

To avoid merge conflicts during the pull request, please first sync your fork. Then, to create a pull request, go to the page of your repository and click the "new pull request" button. All settings should already be set correctly -- all you need to do is enter a title as well as a description for the pull request.

In the next step, the Travis tests will be run automatically. This may take a few minutes due to the size of the code. If the tests fail, you can see the page and check what exactly went wrong. To update your changes, simply push additional changes to your fork.

Be aware that until your pull request is merged, all changes that you push to your fork will automatically be considered part of your pull request. To avoid this, you can create seperate branches and merge these into master instead. To merge a different branch than the default one, simply view the branch on the forked repository page and create a pull request from that page instead.

Once the tests have run, any maintainer can merge the changes into the master branch by clicking the "Merge pull request" button.

## Marking MMT as stable -- updating the stable branch

To mark a version of MMT as stable, we again use pull requests. This time we merge from "master" onto "stable". Again go to the main repository page and select [New pull request](https://github.com/UniFormal/MMT/compare/stable...master). Choose "stable" as the base and "master" as the head. Now write a title as well as a description of the changes that have been made since the last merge to the stable branch. Then create the pull request by clicking the "Create pull request" button.

Only repository owners and administrators can merge this pull request. In order to ensure stability, this additionally requires a [review](https://help.github.com/articles/about-pull-request-reviews/) from a maintainer. To create a review, select the "view changes" button inside the newly created pull request. After looking at the changes made, you can create a review by clicking the "Review changes" button. You can then write a comment as well as either "approve" or "request changes" to the pull request.

Once someone has submitted an approving review and the TRAVIS tests have passed, the pull request can be merged by any maintainer.

## TL;DR

### Updating master
* [Create a fork](https://github.com/Uniformal/MMT/fork) (if you haven't already)
 * ```git clone git@github.com:your-username-here/MMT.git``` -- clone your fork
 * ```git remote add upstream https://github.com/Uniformal/MMT.git``` -- add reference to the original repo
* Sync the fork
  * ```git fetch upstream``` -- get latest changes
  * ```git merge upstream/master``` -- merge them into your current branch
* Make your local changes and ```git push``` them to your fork
* Sync your fork again to avoid merge conflicts
* Make a pull request by going to your fork and clicking "New pull request"
  * wait for the Travis Tests to pass
* Wait until a maintainer merges the pull request

### Updating stable
* Make sure that the code works as intended
* Create a [new pull request](https://github.com/UniFormal/MMT/compare/stable...master) from master onto stable
  * wait for the Travis tests to pass
* wait for a maintainer to make a review and merge the pull request

## Sources and Useful reading

In general GitHub documentation is very helpful for any general questions:

* [GitHub - About forks](https://help.github.com/articles/about-forks/)
* [GitHub - Fork a Repo](https://help.github.com/articles/fork-a-repo)
* [GitHub - Syncing a Fork](https://help.github.com/articles/syncing-a-fork)
* [GitHub - Checking Out a Pull Request](https://help.github.com/articles/checking-out-pull-requests-locally)
* [GitHub - About protected branches](https://help.github.com/articles/about-protected-branches/)
* [GitHub - About pull request reviews](https://help.github.com/articles/about-pull-request-reviews/)

Furthermore parts of this README have been adapted from [https://github.com/OpenJUB/contribution-guidelines/blob/master/github_usage.md](https://github.com/OpenJUB/contribution-guidelines/blob/master/github_usage.md)
