# ReviewBoard Slack Nagger

Do your code reviews in time or get nagged on Slack!

![I want you to do your code reviews.](http://ct.fra.bz/ol/fz/sw/i58/2/12/19/frabz-I-want-you-To-do-your-code-reviews-94f674.jpg)

RB Slack nagger looks for reviews in RB that have not been attended to recently and notifies responsible Slack users.

Note: User emails in Slack and ReviewBoard must match in order for the nagger to be able to link them.

# Usage
Deploy on Heroku and set the following env vars.

## Env vars

 * `SLACK_TOKEN` -> Your slack integration token (so that we can post to Slack)
 * `CRON_EXPR` -> CRON expression specifying when/how often the nagger should run. Defaults to 8:30 AM (in the specified timezone).
 * `TZ` -> Nagger timezone string (defaults to `Europe/Prague`). Used to calculate the idle review time.
 * `RB_URL` -> Your ReviewBoard server URL.
 * `RB_USER` -> RB (admin) user. Used to access RB reviews.
 * `RB_PASSWORD` -> RB (admin) user password.

Note that the proper way to do that would be to have a worker dyno. But in case you don't want to spend money on this, you'll have to set up a pinger so that your dyno doesn't go to sleep. You could set up the New Relic add-on to do that. Or you could use something like [pingu](https://github.com/realyze/pingu).

## TODO:

 * Allow idle time customization (via env var).


## License

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
