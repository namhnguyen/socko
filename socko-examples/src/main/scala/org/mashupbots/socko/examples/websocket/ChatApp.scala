//
// Copyright 2012 Vibul Imtarnasan, David Bolton and Socko contributors.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//   http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
//
package org.mashupbots.socko.examples.websocket

import org.mashupbots.socko.context.HttpResponseStatus
import org.mashupbots.socko.routes._
import org.mashupbots.socko.utils.Logger
import org.mashupbots.socko.webserver.WebServer
import org.mashupbots.socko.webserver.WebServerConfig
import akka.actor.ActorSystem
import akka.actor.Props
import akka.actor.actorRef2Scala
import org.mashupbots.socko.processors.WebSocketBroadcasterRegistration
import org.mashupbots.socko.processors.WebSocketBroadcaster

/**
 * This example shows how to use web sockets, specifically [[org.mashupbots.socko.processors.WebSocketBroadcaster]],
 * for chatting.
 * 
 * With [[org.mashupbots.socko.processors.WebSocketBroadcaster]], you can broadcast messages to all registered web
 * socket connections
 * 
 *  - Open a few browsers and navigate to `http://localhost:8888/html`.
 *  - A HTML page will be displayed
 *  - It will make a web socket connection to `ws://localhost:8888/websocket/`
 *  - Type in some text on one browser and see it come up on the other browsers
 */
object ChatApp extends Logger {
  //
  // STEP #1 - Define Actors and Start Akka
  // `ChatProcessor` is created in the route and is self-terminating
  //
  val actorSystem = ActorSystem("ChatExampleActorSystem")
  val webSocketBroadcaster = actorSystem.actorOf(Props[WebSocketBroadcaster], "webSocketBroadcaster")

  //
  // STEP #2 - Define Routes
  // Each route dispatches the request to a newly instanced `WebSocketProcessor` actor for processing.
  // `WebSocketProcessor` will `stop()` itself after processing the request. 
  //
  val routes = Routes({

    case HttpRequest(rq) => rq match {
      case GET(Path("/html")) => {
        // Return HTML page to establish web socket
        actorSystem.actorOf(Props[ChatProcessor]) ! rq
      }
      case Path("/favicon.ico") => {
        // If favicon.ico, just return a 404 because we don't have that file
        rq.response.write(HttpResponseStatus.NOT_FOUND)
      }
    }

    case WebSocketHandshake(wsHandshake) => wsHandshake match {
      case Path("/websocket/") => {
        // To start Web Socket processing, we first have to authorize the handshake.
        // This is a security measure to make sure that web sockets can only be established at your specified end points.
        wsHandshake.authorize()
        
        // Register this connection with the broadcaster
        webSocketBroadcaster ! new WebSocketBroadcasterRegistration(wsHandshake)
      }
    }

    case WebSocketFrame(wsFrame) => {
      // Once handshaking has taken place, we can now process frames sent from the client
      actorSystem.actorOf(Props[ChatProcessor]) ! wsFrame
    }

  })

  //
  // STEP #3 - Start and Stop Socko Web Server
  //
  def main(args: Array[String]) {
    val webServer = new WebServer(WebServerConfig(), routes, actorSystem)
    Runtime.getRuntime.addShutdownHook(new Thread {
      override def run { webServer.stop() }
    })
    webServer.start()

    System.out.println("Open a few browsers and navigate to http://localhost:8888/html. Start chatting!")
  }
}