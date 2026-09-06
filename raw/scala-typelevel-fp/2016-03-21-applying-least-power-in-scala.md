# Applying Least Power in Scala

> Source: https://eddsteel.com/posts/least-power
> Collected: 2026-09-07
> Published: 2016-03-21

This is the full original article by Edd Steel, reproduced verbatim (converted from HTML, minor formatting cleanup only).

---

I've learned a lot from the people I work with. One such person is [Uji](https://github.com/ujihisa), who is always happy to give functional programming advice, but more importantly, always happy to explain the basis for it. There's usually a general and well-thought out principle at work.

One rule comes up in discussion and code review so much it's now known as "the Uji Principle". You may know it by a more descriptive name. It's best explained by example, in this case incrementing the values in a list.

```scala
// The map way, apply a function to every element.
//
def incMap(l: List[Int]): List[Int] =
  l.map(_ + 1)

// The fold way, accumulate a result by recursively combining
// elements.
//
def incFold(l: List[Int]): List[Int] =
  l.foldRight(List.empty[Int]){ (next, acc) =>
    (next + 1) :: acc
  }

// The recursive way, use the structure of the list to repeat an
// operation on elements.
//
def incRecurse(l: List[Int]): List[Int] =
  l match {
    case Nil => Nil
    case (h::tl) => h + 1 :: incRecurse(tl)
  }

// The mutation way, create a new list and populate it by looping
// over the old one.
//
def incMutate(l: List[Int]): List[Int] = {
  var newList = List[Int]()
  for (i <- l) newList = newList :+ (i + 1)
  newList
}

// The shared mutable memory way (let's just plumb the depths here)
//
import scala.collection.mutable.ListBuffer
object Store {
  var myInts: ListBuffer[Int] = _
}
def incSideEffect(l: List[Int]): List[Int] = {
  Store.myInts = ListBuffer.empty[Int]
  for (i <- l) Store.myInts += (i + 1)
  Store.myInts.toList
}

// Test they work, make a table of results.
//
println("|Name|Result|")
println("|-+-|")
println("|<l>|<c>|")
test("map", incMap)
test("fold right", incFold)
test("recurse", incRecurse)
test("mutate", incMutate)
test("side effect", incSideEffect)

//  Output a tick for yes, cross for no.
//
def test(name: String, f: List[Int] => List[Int]) = {
  val works = f(List(1,2,3)) == List(2,3,4)
  val result = if (works) "✔" else "✘"

  println(s"|$name| $result |")
}
```

They all seem to work. Here's the output.

  Name          Result
  ------------- --------
  map           ✔
  fold right    ✔
  recurse       ✔
  mutate        ✔
  side effect   ✔

I think anyone who understands that code will say that the first solution is better than the last. They'd probably also agree the solutions start with the best and progressively get worse.

So why is `map` a better solution than shared memory in this case? Or even than `foldRight`? The answer -- the principle -- is simple: it is the most specific operation to solve the problem. You can rewrite any use of `map` with `foldRight` but not the other way around, and this relationship exists with every possible pair of operations; they are ordered.

With this ordering, `map` is the most constrained. It's an abstraction at just the right level. It's the least powerful thing that can work. To apply the Uji principle to find the better of two options simply start with the option that is the least powerful, and if it works, you're done.

This is known in Computer Science and blogs as the Rule or Principle of Least Power[^1]. Here it is stated by the [W3C](https://www.w3.org/) in 2001, applied to choosing an implementation language.

> When designing computer systems, one is often faced with a choice between using a more or less powerful language for publishing information, for expressing constraints, or for solving some problem. This finding explores tradeoffs relating the choice of language to reusability of information. The "Rule of Least Power" suggests choosing the least powerful language suitable for a given purpose.
>
> -- W3C, [Rule of Least Power](https://www.w3.org/2001/tag/doc/leastPower.html)

## Rúnar Bjarnason's "Constraints liberate, liberties constrain" 

Back to Scala, which has literally never been described as the least powerful language suitable for a purpose. We know the principle applies (ask Uji) but what do we mean by "power" here? A good description was given by Rúnar Bjarnason in his [talk](https://www.youtube.com/watch?v=GqmsQeSzMdw) "Constraints liberate, liberties constrain". You should watch it, it's great!

Rúnar talks about least privilege. Here, when we talk about more powerful, we mean unconstrained, at the cost of compositionality and modularity. "The more a system *can* do, the less we can predict what it *will* do".

My `map` example is the least concrete -- it deals with an abstraction at the right level to solve the problem, where implementation details are provided by something else. To use Rúnar's language, it "detonates" later than the shared memory, entirely concrete implementation. Think about the effort to make the first example run in parallel, compared to the last.

It's worth taking the time to get this straight in your mind; counter-intuitively, a more abstract, more generic function is less powerful according to this definition. That doesn't mean it's less useful or less reusable, quite the opposite in fact, that's the point.

Of course you could go further, we would be relying on less "powerful", more abstract mechanisms if our method signature was written specifically in terms of "things that can be mapped over" (functors) and "things that can take part in a binary associative operation" (semigroups) and according to this principle that would be an improvement.

## Li Haoyi's "Principle of Least Power" 

Recently Li Haoyi wrote [a wonderful article](http://www.lihaoyi.com/post/StrategicScalaStylePrincipleofLeastPower.html) describing what it means to apply this principle to Scala development in general. Like the element incrementer functions above, he orders various approaches by power in categories:

- immutability
- published API
- data types
- error handling
- asynchronous return types
- dependency injection

### A Quibble 

It's a really good reference and I agree with almost everything in it. However I would like to quibble with one point, and this is the Internet so I will. [Ken Scambler](http://www.lihaoyi.com/post/StrategicScalaStylePrincipleofLeastPower.html#comment-2513931369) pointed it out too. "You should always use built-in primitives and collections" might lead you to miss out on some of the benefits of a type checker. If your function expects a user ID, it should not accept a `String`. Not unless the complete works of Shakespeare is a valid user ID[^2]. And this is completely in line with our version of what least power means for Scala: choose the most constrained abstraction that will work.

### The best bit 

But one thing stuck out to me as very definitely correct, and probably controversial to many readers:

> In general, if you find yourself passing an object to a method that only calls a single method, consider making that method take a FunctionN and passing in a lambda.
>
> -- Li Haoyi, [Principle of Least Power](http://www.lihaoyi.com/post/StrategicScalaStylePrincipleofLeastPower.html#functions)

This ties in very well with the [Dependency Injection](http://www.lihaoyi.com/post/StrategicScalaStylePrincipleofLeastPower.html#dependency-injection) section. Instead of injecting dependencies through mix-ins and constructors, pass things (i.e. probably functions) to the methods that need them. That applies equally to test code, where suddenly mocks aren't nearly so important.

I'd like to explore these ideas further because they can be directly applied to the sort of code we write every day. So then, what does he mean? He means if your business logic requires access to a user's ID, pass in a user ID, not a user. If your business logic needs to constuct a `Widget` then use that to calculate some stuff, pass in a `ID => Widget` function that handles that construction, don't put the logic in a trait that's self-typed to `WidgetProvider` instantiated in a boot class along with widget constuction and seven unrelated things `with WidgetSauce with WidgetRules with God with OhGod with OhGodOhGodOhGod`.

But what of our `AbstractServiceDecoratorComponent`? How can we show off our enterprise test mocking libraries? What will become of our inscrutable and many-layered cake?? I understand, it sounds controversial. It will be OK.

I've been taking this idea to heart the past couple of weeks and I have to say I think it's wonderful advice. It has made code and tests clearer, quicker to write and easier to reason about. Alright, enough exposition, this is Scala. Let's look at contrived code.

## A service example 

Our imaginary startup has been running in stealth mode for several months, and our rockstar ninja pirate back-end developers have already built a fully reactive user service. The system is up and gathering user information. Here's our user model, with the business-critical information we store:

```scala
package com.eddsteel.posts.leastpower

import java.time.{LocalDate => Date, ZoneOffset => TimeZone}

package object model {

  final case class User(
    id: ID, birthday: Date, timezone: TimeZone,
    favoriteThing: String, pets: Set[Pet], whichFriendAreYou: Friend)

  type ID = Long
  type Pet = String

  sealed trait Friend
  object Friends {
    case object Joey extends Friend
    case object Phoebe extends Friend
    case object Chandler extends Friend
    case object Rachel extends Friend
    case object Monica extends Friend
    // we definitely don't need Ross in our MVP. No one likes you Ross.
  }
}
```

Users are available to the rest of the system over a `UserService` client. We use that to call the service. Given an `ID` it will return us a `Future[Option[Long]]`, that is an asynchronous value that may be nothing (if the user is not found) and may be a user. In addition, `Future` values model potential failure. Instead of an `Option[User]` we may find our future has failed, in which case it holds the cause `Throwable`. `Future` is useful for this example but if it offends you please feel free to substitute your concurrency primitive of choice.

### Let's build an endpoint 

Good news! We have pivoted, again, and will now be offering a gift delivery service to registered users of our app. This will generate revenue, I mean obviously.

In order to do this, we have built a gift service and a `GiftService` client[^3]. Now we just need to tie that together in a web app our front-end will call. Don't worry, they'll only call it once per day per user, and delivery is instant!

The front-end will `POST` to `/users/:id/gift` and expect us to order a gift of the user's favourite thing, if it's the user's birthday (via the gift service). It will expect to receive a status message (for the web user to read) and the gift's ID (for the machines) if a gift was ordered. Simple.

```scala
package com.eddsteel.posts.leastpower
package endpoints

import model._
import services._
import webapp.{Request, Response, NotFoundException}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try
import java.time.LocalDate

/** Endpoints for our Web app.
 *
 *  The important feature: given a user ID, will order that user a gift
 *  if it's their birthday.
 *
 */
trait EndpointsV1 {
  this: UserServiceComponent with GiftServiceComponent =>
  import EndpointsV1._

  def postGift(req: Request)(implicit ec: ExecutionContext): Future[Response] = {
    val response = for {
      // extract ID from request path
      userId <- Future.fromTry(WebApp.extractAndValidateUserId(req))
      // retrieve the user
      maybeUser <- userService.getById(userId)
      // bail with 404 if we have no user.
      user = maybeUser.getOrElse(throw NotFoundException(userId))
      // check if it's the user's birthday
      isBirthday = isToday(user.birthday)
      // call gift service if necessary
      gift <- if (isBirthday) giftService.order(userId, user.favoriteThing)
              else Future.successful(("not today", None))
      // build success response
      response <- Future.fromTry(WebApp.mkResponse(gift))
    } yield response

    // build error response
    response.recover(WebApp.mkErrorResponse)
  }
}

object EndpointsV1 {
  private def isToday(birthday: LocalDate): Boolean = {
    // using this utility helps us write tests.
    val today = DateTimeUtils.now.toLocalDate

    today.getDayOfMonth == birthday.getDayOfMonth &&
    today.getMonth == birthday.getMonth
  }

}
```

For the purpose of discussion let's assume, let's just say, that the framework "WebApp" is separate from this service and well tested.[^4] Hopefully with a little imagination this code is fairly self-explanatory a flow of information from request, through services to response. We get the ID, we get the user, if the user's birthday is today we get the gift sent, and then we respond. See below for more information.[^5]

The `isToday`, `mkResponse`, `mkErrorResponse`, `extractAndValidateUserId` methods are used and tested elsewhere, ditto the gruesome details of routing and data validation. In this context, the only new thing is the middle few lines of `postGift`.

Still, we're professionals, and we always practice responsible testing for our systems. We certainly want to test those lines. Three, four lines won't need much test code.

```scala
package com.eddsteel.posts.leastpower
package endpoints

import model._
import services._
import webapp._

import org.scalatest.{FlatSpec, Matchers}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mock.MockitoSugar
import org.mockito.Mockito.{when, verify, RETURNS_SMART_NULLS}

import scala.concurrent.{Future, ExecutionContext}
import java.time.{ZoneOffset, LocalDate, ZonedDateTime, ZoneId}

class EndpointTestsV1 extends FlatSpec with Matchers with MockitoSugar with ScalaFutures {

  import ExecutionContext.Implicits.global

  // mock out our dependencies.
  // if we don't provide "RETURNS_SMART_NULLS" no effort will be taken
  // to explain the NullPointerExceptions thrown by misconfigured mocks.
  //
  val mockUserService = mock[UserService](RETURNS_SMART_NULLS)
  val mockGiftService = mock[GiftService](RETURNS_SMART_NULLS)
  val controller = new Controller with EndpointsV1 with UserServiceComponent with GiftServiceComponent {
    override val userService = mockUserService
    override val giftService = mockGiftService

    // all routes go to the endpoint we're testing.
    override def route(method: Method, path: String): Action = postGift
  }

  "POST /users/:id/gift" should "order a gift for a user on their birthday" in {
    // make the world ready for our test
    //
    DateTimeUtils.setDateTime(ZonedDateTime.of(2015, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC))

    // mock the return of a user that contains the right
    // information (and a bunch of irrelevant stuff too, because we
    // have to)
    //
    when(mockUserService.getById(1L)).thenReturn(
      Future.successful(Some(
        User(id = 1, birthday = LocalDate.of(1980, 1, 1), favoriteThing = "booze",
             timezone = ZoneOffset.UTC, pets = Set.empty, whichFriendAreYou = Friends.Phoebe))))

    // mock the gift service to just return a message saying it
    // successfully ordered the favorite thing (we're not testing this
    // service)
    //
    when(mockGiftService.order(1L, "booze")).thenReturn(
      Future.successful(
        ("booze ordered for user 1", Some(1L))))

    // Send the request through the routing logic to the endpoint to test the endpoint
    // logic.
    //
    val response = WebApp.postFakeRequest(controller, POST, "/users/1/gift", "")

    response.futureValue.shouldEqual("(booze ordered for user 1,Some(1))")
    verify(mockUserService).getById(1L)
    verify(mockGiftService).order(1L, "booze") // check the gift actually was ordered through the service
  }
}
```

``` example

[info] EndpointTestsV1:
[info] POST /users/:id/gift
[info] - should order a gift for a user on their birthday
[info] Run completed in 485 milliseconds.
[info] Total number of tests run: 1
[info] Suites: completed 1, aborted 0
[info] Tests: succeeded 1, failed 0, canceled 0, ignored 0, pending 0
[info] All tests passed.
[success] Total time: 10 s, completed Mar 21, 2016 11:32:00 PM
```scala

Well.

Given a text-processing problem a developer might choose to use regular expressions, and now she has two problems. Given a testing problem a developer might choose to use mocks, and now blood is gushing from her eyesockets[^6]. Let's take a moment and look at why they were necessary.

The logic we're testing needs to get a user based on an ID, so we mock the service that does that. Also we need to stub out the rest of the model, even though we don't want or need those details. And based on the logic we are testing a gift should be ordered, so we mock that service too. In order to test the logic, because it is buried inside an endpoint action, we need to construct a request, and pass it through our framework in order to get something we can inspect.

In order to test time-specific logic, as in "is it the user's birthday today?" we have a utility to fetch "now". Usually it returns the current time, but in tests we can override it.

Maybe we can lose some of this.

### Narrow the scope of test 

We're constructing a whole fake set of endpoints in order to test specific behaviour. Our test covers code for routing, error recovery, and service calls but we're not actually testing it. Also our eye sockets are bleeding quite a lot. Let's remember our framework is well covered by existing tests, and extract just the logic we want to test. While we're at it, let's leave out the user fetching. We're not testing that. We'll want to test error handling of this logic, but not in this test.

While we're at that, let's start thinking least-power and pass in the dependencies like service clients instead of plucking them from the scope of the local instance. Data in, data out would be far easier to deal with[^7].

Behold, `maybeSendGift`!

```
package com.eddsteel.posts.leastpower
package endpoints

import model._
import services._
import webapp.{Request, Response, NotFoundException}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try
import java.time.LocalDate

/** Let's change the test to ignore the request/response/routing stuff and just test the business logic.
  *
  * It would be easier to test this logic if we made the dependencies
  * explicit (method params), and moved it out to the companion object.
  */
trait EndpointsV2 {
  this: UserServiceComponent with GiftServiceComponent =>
  import EndpointsV2._

  def postGift(req: Request)(implicit ec: ExecutionContext): Future[Response] = {
    val response = for {
      // extract ID from request path
      userId <- Future.fromTry(WebApp.extractAndValidateUserId(req))
      // retrieve the user
      maybeUser <- userService.getById(userId)
      // bail with 404 if we have no user.
      user = maybeUser.getOrElse(throw NotFoundException(userId))
      // check if it's the user's birthday, and maybe send them a gift.
      gift <- maybeSendGift(user, giftService)
      // build success response
      response <- Future.fromTry(WebApp.mkResponse(gift))
    } yield response

    // build error response
    response.recover(WebApp.mkErrorResponse)
  }
}

object EndpointsV2 {
  private def isToday(birthday: LocalDate): Boolean = {
    // using this utility helps us write tests.
    val today = DateTimeUtils.now.toLocalDate

    today.getDayOfMonth == birthday.getDayOfMonth &&
    today.getMonth == birthday.getMonth
  }

  private[endpoints] def maybeSendGift(user: User, giftService: GiftService) = {
    val birthdayToday = isToday(user.birthday)
    if (birthdayToday) giftService.order(user.id, user.favoriteThing)
    else Future.successful(("not today", None))
  }
}
```scala

Let's see how much test we can throw away.

```
package com.eddsteel.posts.leastpower
package endpoints

import model._
import services._
import webapp._

import org.scalatest.{FlatSpec, Matchers}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mock.MockitoSugar
import org.mockito.Mockito.{when, verify, RETURNS_SMART_NULLS}

import scala.concurrent.{Future, ExecutionContext}
import java.time.{ZoneOffset, LocalDate, ZonedDateTime, ZoneId}

class EndpointTestsV2 extends FlatSpec with Matchers with MockitoSugar with ScalaFutures {

  import ExecutionContext.Implicits.global

  val mockGiftService = mock[GiftService](RETURNS_SMART_NULLS)

  "maybeSendGift" should "order a gift for a user on their birthday" in {
    DateTimeUtils.setDateTime(ZonedDateTime.of(2015, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC))

    val fakeUser =
      User(id = 1, birthday = LocalDate.of(1980, 1, 1), favoriteThing = "booze",
           timezone = ZoneOffset.UTC, pets = Set.empty, whichFriendAreYou = Friends.Phoebe)

    when(mockGiftService.order(1L, "booze")).thenReturn(
      Future.successful(("booze ordered for user 1", Some(1L))))

    val response = EndpointsV2.maybeSendGift(fakeUser, mockGiftService)
    response.futureValue.shouldEqual(("booze ordered for user 1", Some(1L)))
    verify(mockGiftService).order(1L, "booze") // check the gift actually was ordered through the service
  }
}
```scala

``` example

[info] EndpointTestsV2:
[info] maybeSendGift
[info] - should order a gift for a user on their birthday
[info] Run completed in 409 milliseconds.
[info] Total number of tests run: 1
[info] Suites: completed 1, aborted 0
[info] Tests: succeeded 1, failed 0, canceled 0, ignored 0, pending 0
[info] All tests passed.
[success] Total time: 1 s, completed Mar 21, 2016 11:32:07 PM
```

Wow, that test is like half the size, and still covers what we needed!

Jessica Kerr wrote a great guide on [writing tests without mocks](http://engineering.monsanto.com/2015/07/28/avoiding-mocks/), and we've used the "flow of data" approach to remove the user service mock. Unfortunately we can't avoid mocking the gift service by pre-ordering a gift, because we don't know outside of `maybeSendGift` if we want one.

Still let's count our blessings:

1.  Don't have to build a fake endpoints object or pretend we know how the framework works.
2.  We don't have to fear any future change to endpoints, our method stands alone.
3.  We don't have to mock a user service, we just ask for a user.
4.  Our eyesocket bleed is down to a low trickle.

Four blessings! And we're not done yet. There's still a mock to replace, and the article also describes a "port and adapters approach" which we'll see ties in well with the least power principle.

The other distasteful part of this code is that because we are exchanging domain objects, a whole bunch of unneccessary information is being carried along with what we care about. Why must our test specify the test user most resembles Phoebe? Our test doesn't care, but the compiler does. The compiler does because the data model does, because four versions ago it had business value. Talk about incidental complexity.

Additionally, our convenient "now" utility relies on some fairly powerful scary stuff. It stops the world and sets universal time. There must be a better way.

### Reduce Power 

Let's apply the principle some more. That means:

- replace compound values with single values we actually need.
- replace objects whose methods we use with functions
- replace "dynamic variables" with method parameters

The first is obvious, the second two less so, but we'll see they give us a lot.

```scala
package com.eddsteel.posts.leastpower
package endpoints

import model._
import services._
import webapp.{Request, Response, NotFoundException}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try
import java.time.{LocalDateTime, LocalDate}

 /* maybeSendGift still has some dependencies (in the parameters or
  * environment)
  *
  * - gift service (in order to call the order method)
  * - date time utils.
  *
  * By the principle of least power, let's pass those into the method
  * as functions instead.  let's also use plain values and functions
  * rather than containers of plain values and methods.
  */
 trait EndpointsV3 {
   this: UserServiceComponent with GiftServiceComponent =>
   import EndpointsV3._

   def postGift(req: Request)(implicit ec: ExecutionContext): Future[Response] = {
     val response = for {
       // extract ID from request path
       userId <- Future.fromTry(WebApp.extractAndValidateUserId(req))
       // retrieve the user
       maybeUser <- userService.getById(userId)
       // bail with 404 if we have no user.
       user = maybeUser.getOrElse(throw NotFoundException(userId))
       // check if it's the user's birthday, and maybe send them a gift
       gift <- maybeSendGift(user.id, user.birthday, user.favoriteThing,
                             LocalDateTime.now, giftService.order)
       // build success response
       response <- Future.fromTry(WebApp.mkResponse(gift))
     } yield response

     // build error response
     response.recover(WebApp.mkErrorResponse)
   }
 }

 object EndpointsV3 {
   def isToday(birthday: LocalDate, now: => LocalDateTime) =
     now.getDayOfMonth == birthday.getDayOfMonth &&
     now.getMonth == birthday.getMonth

   def maybeSendGift(userId: ID, birthday: LocalDate, favoriteThing: String,
                     now: => LocalDateTime,
                     orderGift: (Long, String) => Future[(String, Option[Long])])
                    (implicit ec: ExecutionContext) = {
     val birthdayToday = isToday(birthday, now)
     if (birthdayToday) orderGift(userId, favoriteThing)
     else Future.successful(("not today", None))
   }
 }
```

We've added some overhead to our maybeSendGift. We've chosen more constraints. For example, instead of sending a `GiftService` the endpoint now sends a specific gift posting function (which happens to use a gift service). This is straight out of the Li playbook, and also what Kerr described as the "ports and adapters approach". It's is slightly more code, and the signature of `maybeSendGift` is now bigger than the body. On the other hand, we know as aspiring functional programmers that's the better way around anyway. Also, we've lost some code in `isToday` as we no longer need to use the powerful, timezone-aware utility. And we haven't even got to the test yet.

```scala
package com.eddsteel.posts.leastpower
package endpoints

import model._
import services._
import webapp._

import org.scalatest.{FlatSpec, Matchers}
import org.scalatest.concurrent.ScalaFutures

import scala.concurrent.{Future, ExecutionContext}
import java.time.{LocalDateTime, LocalDate}

class EndpointsV3Test extends FlatSpec with Matchers with ScalaFutures {
  import ExecutionContext.Implicits.global

  "maybeSendGift" should "order a gift for a user on their birthday" in {

    def successfulGiftOrder(id: Long, thing: String) =
      Future.successful((s"$thing ordered for user $id", Some(id)))

    val result =
      EndpointsV3.maybeSendGift(1, LocalDate.of(1980, 1, 1), "booze",
                                LocalDateTime.of(2016, 1, 1, 0, 0, 0, 0),
                                successfulGiftOrder)

    result.futureValue.shouldEqual(("booze ordered for user 1", Some(1)))

  }
}
```

``` example

[info] EndpointsV3Test:
[info] maybeSendGift
[info] - should order a gift for a user on their birthday
[info] Run completed in 311 milliseconds.
[info] Total number of tests run: 1
[info] Suites: completed 1, aborted 0
[info] Tests: succeeded 1, failed 0, canceled 0, ignored 0, pending 0
[info] All tests passed.
[success] Total time: 1 s, completed Mar 21, 2016 11:32:13 PM
```scala

Boom! Quite a bit shorter. Our test is now 3 (long) lines and can live entirely outside our service framework. We're dealing with simple data and functions which opens up opportunites like generative testing that would have been very painful before.

Even better, despite the dependency on some sort of gift ordering logic, we don't need to mock anything. We don't need to mock anything!

But don't we need mocks, not fakes? what about validating our gift service was called? There's no need, because the only way for it to produce the expected output is for it to be called with the correct parameters.

There's still room for improvement in our code -- we're still using mix-ins to bring dependent services into our endpoints code. If we can change our service framework, we should look at that next. Is "exercise for the reader" a cop-out? We'll leave that as an exercise for the reader.

## Worth it?

Hopefully you want code more like example 3 than example 1, if so I think you'll agree the principle of least power is a useful guide to get you there. Let's summarize.

- The Principle of Least Power can help thinking about the complexity of your code, and reducing it.
- Following it can make your code less tightly coupled, easier to test, and easier to reason about.
- The cost is extra function parameters (which can be mitigated as necessary with case classes) and an extra level of indirection.
- Smart use of names and types can make the code just as expressive and clear as before (more so, actually),
- Remember that when it comes to abstraction, constraints liberate and liberties constrain.

Really this could be summarised as "prefer plain data and pure functions, and the pain will go away", but hopefully the noisy details help convince you of that.

## Ack

Thanks to [Li Haoyi](https://twitter.com/li_haoyi), [Rúnar Bjarnason](https://twitter.com/runarorama), [Jessica Kerr](https://twitter.com/jessitron), [Ken Scambler](https://twitter.com/kenscambler) and [Uji](https://twitter.com/ujm). This is based on some ideas I talked about at a recent Scala Guild meeting at Hootsuite, which covered the "Principle of Least Power". At the same guild meeting my colleague [Johnny](https://twitter.com/typesake) presented covering the "Constraints liberate, liberties constrain" talk, and my colleague [Ryan](https://twitter.com/ryanheinen) reminded me I'd linked to "Testing without Mocking" before. You could read more about [Hootsuite](http://code.hootsuite.com/) and [Guilds](http://code.hootsuite.com/guilds/). Needless to say I don't speak for my employer, or those I've quoted here. Any errors or inaccuracies in this post are mine. Thanks also to Emacs and org-mode. You routinely blow my mind.

## Source

- `least-power.org` ([view](least-power.org.html))([download](least-power.org))
- `build.sbt` ([view](lp/build.sbt.html)) ([download](lp/build.sbt))
- `datetimeutils.scala` ([view](lp/datetimeutils.scala.html)) ([download](lp/datetimeutils.scala))
- `model.scala` ([view](lp/model.scala.html)) ([download](lp/model.scala))
- `service-endpoints-v1.scala` ([view](lp/service-endpoints-v1.scala.html)) ([download](lp/service-endpoints-v1.scala))
- `service-endpoints-v2.scala` ([view](lp/service-endpoints-v2.scala.html)) ([download](lp/service-endpoints-v2.scala))
- `service-endpoints-v3.scala` ([view](lp/service-endpoints-v3.scala.html)) ([download](lp/service-endpoints-v3.scala))
- `services.scala` ([view](lp/services.scala.html)) ([download](lp/services.scala))
- `test_service-endpoints-v1.scala` ([view](lp/test_service-endpoints-v1.scala.html)) ([download](lp/test_service-endpoints-v1.scala))
- `test_service-endpoints-v2.scala` ([view](lp/test_service-endpoints-v2.scala.html)) ([download](lp/test_service-endpoints-v2.scala))
- `test_service-endpoints-v3.scala` ([view](lp/test_service-endpoints-v3.scala.html)) ([download](lp/test_service-endpoints-v3.scala))
- `webapp.scala` ([view](lp/webapp.scala.html)) ([download](lp/webapp.scala))
- [`leastpower-code.tar.gz`](lp/leastpower-code.tar.gz)

## Footnotes: 

[^1]

A similar rule in Information Security is the rule of least privilege, which is useful for similar, but distinct, reasons).

[^2]

And now I'm directly plagiarizing him. Do read the comment.

[^3]

`GiftService` provides `orderGift`, of type `(ID, String) => Future[GiftService.Response]`. Given a description of a gift and the recipient's ID, it will order a gift for that person. The response contains what the webapp client needs, we'll pass it through.

[^4]

For the sake of example and NIH, we'll imagine the service was written with a custom [library](lp/webapp.scala.html), which resembles a blend of scalatra, Play, http4s, spray etc for its own unique choice of abstractions. Yes this code compiles, no it doesn't serve HTTP traffic.

[^5]

Let's briefly cover what this code does. It creates a trait with some service dependencies, containing a single HTTP action. The action, using these dependencies, accepts a user ID, gets user information, checks if the user's birthday is today, and if so uses the gift service to order that user a gift. Functions that require no access to dependencies or shared state live in the companion object, so no instance-specific information creeps in.\
\
The endpoint's flow is modelled as a for-comprehension over `scala.concurrent.Future`, flatmapping over future values means that as results become available the functions that use them are run (maybe on other threads). Each line relies on values retrieved in previous ones, so though these calls may be asynchronous and non-blocking they must happen sequentially. Those that are synchronous are simply wrapped, i.e. no thread switching takes place, but the compiler is assured it can treat these immediately available values as future values. If any step produces a failed future, the \`flatMap\` calls will not run the following lines (flatmap on a failed future is simply that failed future) and the endpoint recovers such a failure into an appropriate response.

[^6]

I think the best argument against mocking frameworks is the dreary experience of using them. I'm not hating on Mockito specifically, here, but the whole miserable idea. If you don't agree I invite you to try a mocking framework for yourself. See? Sorry about your eyesockets.

[^7]

I know, we're not actually there yet. Keep going.

Created: 2016-03-21 Mon 23:32
