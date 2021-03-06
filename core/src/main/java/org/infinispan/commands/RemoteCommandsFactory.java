/*
 * JBoss, Home of Professional Open Source
 * Copyright 2009 Red Hat Inc. and/or its affiliates and other
 * contributors as indicated by the @author tags. All rights reserved.
 * See the copyright.txt in the distribution for a full listing of
 * individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.infinispan.commands;

import org.infinispan.CacheException;
import org.infinispan.commands.control.CacheViewControlCommand;
import org.infinispan.commands.control.LockControlCommand;
import org.infinispan.commands.control.StateTransferControlCommand;
import org.infinispan.commands.module.ExtendedModuleCommandFactory;
import org.infinispan.commands.module.ModuleCommandFactory;
import org.infinispan.commands.read.DistributedExecuteCommand;
import org.infinispan.commands.read.GetKeyValueCommand;
import org.infinispan.commands.read.MapReduceCommand;
import org.infinispan.commands.remote.CacheRpcCommand;
import org.infinispan.commands.remote.ClusteredGetCommand;
import org.infinispan.commands.remote.MultipleRpcCommand;
import org.infinispan.commands.remote.SingleRpcCommand;
import org.infinispan.commands.remote.recovery.CompleteTransactionCommand;
import org.infinispan.commands.remote.recovery.GetInDoubtTransactionsCommand;
import org.infinispan.commands.remote.recovery.GetInDoubtTxInfoCommand;
import org.infinispan.commands.remote.recovery.TxCompletionNotificationCommand;
import org.infinispan.commands.tx.CommitCommand;
import org.infinispan.commands.tx.PrepareCommand;
import org.infinispan.commands.tx.RollbackCommand;
import org.infinispan.commands.write.ApplyDeltaCommand;
import org.infinispan.commands.write.ClearCommand;
import org.infinispan.commands.write.InvalidateCommand;
import org.infinispan.commands.write.InvalidateL1Command;
import org.infinispan.commands.write.PutKeyValueCommand;
import org.infinispan.commands.write.PutMapCommand;
import org.infinispan.commands.write.RemoveCommand;
import org.infinispan.commands.write.ReplaceCommand;
import org.infinispan.factories.GlobalComponentRegistry;
import org.infinispan.factories.KnownComponentNames;
import org.infinispan.factories.annotations.ComponentName;
import org.infinispan.factories.annotations.Inject;
import org.infinispan.factories.scopes.Scope;
import org.infinispan.factories.scopes.Scopes;
import org.infinispan.manager.EmbeddedCacheManager;
import org.infinispan.remoting.transport.Transport;

import java.util.Map;

/**
 * Specifically used to create un-initialized {@link org.infinispan.commands.ReplicableCommand}s from a byte stream.
 * This is a {@link Scopes#GLOBAL} component and doesn't have knowledge of initializing a command by injecting
 * cache-specific components into it.
 * <p />
 * Usually a second step to unmarshalling a command from a byte stream (after
 * creating an un-initialized version using this factory) is to pass the command though {@link CommandsFactory#initializeReplicableCommand(ReplicableCommand,boolean)}.
 *
 * @see CommandsFactory#initializeReplicableCommand(ReplicableCommand,boolean)
 * @author Manik Surtani
 * @author Mircea.Markus@jboss.com
 * @since 4.0
 */
@Scope(Scopes.GLOBAL)
public class RemoteCommandsFactory {
   Transport transport;
   EmbeddedCacheManager cacheManager;
   GlobalComponentRegistry registry;
   Map<Byte,ModuleCommandFactory> commandFactories;

   @Inject
   public void inject(Transport transport, EmbeddedCacheManager cacheManager, GlobalComponentRegistry registry,
                      @ComponentName(KnownComponentNames.MODULE_COMMAND_FACTORIES) Map<Byte, ModuleCommandFactory> commandFactories) {
      this.transport = transport;
      this.cacheManager = cacheManager;
      this.registry = registry;
      this.commandFactories = commandFactories;
   }

   /**
    * Creates an un-initialized command.  Un-initialized in the sense that parameters will be set, but any components
    * specific to the cache in question will not be set.
    * <p/>
    * You would typically set these parameters using {@link CommandsFactory#initializeReplicableCommand(ReplicableCommand,boolean)}
    * <p/>
    *
    *
    * @param id id of the command
    * @param parameters parameters to set
    * @param type
    * @return a replicable command
    */
   public ReplicableCommand fromStream(byte id, Object[] parameters, byte type) {
      ReplicableCommand command;
      if (type == 0) {
         switch (id) {
            case PutKeyValueCommand.COMMAND_ID:
               command = new PutKeyValueCommand();
               break;
            case PutMapCommand.COMMAND_ID:
               command = new PutMapCommand();
               break;
            case RemoveCommand.COMMAND_ID:
               command = new RemoveCommand();
               break;
            case ReplaceCommand.COMMAND_ID:
               command = new ReplaceCommand();
               break;
            case GetKeyValueCommand.COMMAND_ID:
               command = new GetKeyValueCommand();
               break;
            case ClearCommand.COMMAND_ID:
               command = new ClearCommand();
               break;
            case InvalidateCommand.COMMAND_ID:
               command = new InvalidateCommand();
               break;
            case InvalidateL1Command.COMMAND_ID:
               command = new InvalidateL1Command();
               break;
            case DistributedExecuteCommand.COMMAND_ID:
               command = new DistributedExecuteCommand<Object>();
               break;
            case ApplyDeltaCommand.COMMAND_ID:
               command = new ApplyDeltaCommand();
               break;      
            default:
               throw new CacheException("Unknown command id " + id + "!");
         }
      } else {
         ModuleCommandFactory mcf = commandFactories.get(id);
         if (mcf != null)
            return mcf.fromStream(id, parameters);
         else
            throw new CacheException("Unknown command id " + id + "!");
      }
      command.setParameters(id, parameters);
      return command;
   }

   /**
    * Resolve an {@link CacheRpcCommand} from the stream.
    *
    * @param id            id of the command
    * @param parameters    parameters to be set
    * @param type          type of command (whether internal or user defined)
    * @param cacheName     cache name at which this command is directed
    * @return              an instance of {@link CacheRpcCommand}
    */
   public CacheRpcCommand fromStream(byte id, Object[] parameters, byte type, String cacheName) {
      CacheRpcCommand command;
      if (type == 0) {
         switch (id) {
            case LockControlCommand.COMMAND_ID:
               command = new LockControlCommand(cacheName);
               break;
            case PrepareCommand.COMMAND_ID:
               command = new PrepareCommand(cacheName);
               break;
            case CommitCommand.COMMAND_ID:
               command = new CommitCommand(cacheName);
               break;
            case RollbackCommand.COMMAND_ID:
               command = new RollbackCommand(cacheName);
               break;
            case MultipleRpcCommand.COMMAND_ID:
               command = new MultipleRpcCommand(cacheName);
               break;
            case SingleRpcCommand.COMMAND_ID:
               command = new SingleRpcCommand(cacheName);
               break;
            case ClusteredGetCommand.COMMAND_ID:
               command = new ClusteredGetCommand(cacheName);
               break;
            case StateTransferControlCommand.COMMAND_ID:
               command = new StateTransferControlCommand(cacheName);
               break;
            case RemoveCacheCommand.COMMAND_ID:
               command = new RemoveCacheCommand(cacheName, cacheManager, registry);
               break;
            case TxCompletionNotificationCommand.COMMAND_ID:
               command = new TxCompletionNotificationCommand(cacheName);
               break;
            case GetInDoubtTransactionsCommand.COMMAND_ID:
               command = new GetInDoubtTransactionsCommand(cacheName);
               break;
            case MapReduceCommand.COMMAND_ID:
               command = new MapReduceCommand(cacheName);
               break;
            case GetInDoubtTxInfoCommand.COMMAND_ID:
               command = new GetInDoubtTxInfoCommand(cacheName);
               break;
            case CompleteTransactionCommand.COMMAND_ID:
               command = new CompleteTransactionCommand(cacheName);
               break;
            case CacheViewControlCommand.COMMAND_ID:
               command = new CacheViewControlCommand(cacheName);
               break;                      
            default:
               throw new CacheException("Unknown command id " + id + "!");
         }
      } else {
         ExtendedModuleCommandFactory mcf = (ExtendedModuleCommandFactory) commandFactories.get(id);
         if (mcf != null)
            return mcf.fromStream(id, parameters, cacheName);
         else
            throw new CacheException("Unknown command id " + id + "!");
      }
      command.setParameters(id, parameters);
      return command;
   }
}
