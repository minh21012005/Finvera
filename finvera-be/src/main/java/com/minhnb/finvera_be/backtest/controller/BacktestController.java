package com.minhnb.finvera_be.backtest.controller;

import static com.minhnb.finvera_be.backtest.dto.BacktestDtos.*;
import com.minhnb.finvera_be.backtest.service.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.*;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController @Validated @RequestMapping("/api/v1/backtests")
public class BacktestController {
    private final BacktestCommandService commands;private final BacktestQueryService queries;
    public BacktestController(BacktestCommandService commands,BacktestQueryService queries){this.commands=commands;this.queries=queries;}
    @PostMapping public ResponseEntity<RunSummary> create(@Valid @RequestBody CreateRequest request,@RequestHeader(name="Idempotency-Key",required=false) String key){var run=commands.create(request,key);return ResponseEntity.accepted().location(URI.create("/api/v1/backtests/"+run.id())).body(run);}
    @GetMapping public Page<RunSummary> list(@RequestParam(defaultValue="100") @Min(1) @Max(500) int limit,@RequestParam(defaultValue="0") @Min(0) int offset){return queries.list(limit,offset);}
    @GetMapping("/{id}") public RunDetail detail(@PathVariable UUID id){return queries.detail(id);}
    @GetMapping("/{id}/trades") public Page<Trade> trades(@PathVariable UUID id,@RequestParam(defaultValue="100") @Min(1) @Max(500) int limit,@RequestParam(defaultValue="0") @Min(0) int offset){return queries.trades(id,limit,offset);}
    @GetMapping("/{id}/equity") public Page<EquityPoint> equity(@PathVariable UUID id,@RequestParam(defaultValue="100") @Min(1) @Max(500) int limit,@RequestParam(defaultValue="0") @Min(0) int offset){return queries.equity(id,limit,offset);}
    @GetMapping("/{id}/events") public Page<EntryEvent> events(@PathVariable UUID id,@RequestParam(defaultValue="100") @Min(1) @Max(500) int limit,@RequestParam(defaultValue="0") @Min(0) int offset){return queries.events(id,limit,offset);}
    @ExceptionHandler(BacktestExceptions.Invalid.class) ResponseEntity<ProblemDetail> invalid(BacktestExceptions.Invalid e){return problem(HttpStatus.UNPROCESSABLE_ENTITY,e.getMessage());}
    @ExceptionHandler(BacktestExceptions.NotFound.class) ResponseEntity<ProblemDetail> missing(){return problem(HttpStatus.NOT_FOUND,"BACKTEST_NOT_FOUND");}
    @ExceptionHandler(BacktestExceptions.ResultUnavailable.class) ResponseEntity<ProblemDetail> unavailable(){return problem(HttpStatus.CONFLICT,"BACKTEST_RESULT_NOT_AVAILABLE");}
    private static ResponseEntity<ProblemDetail> problem(HttpStatus status,String reason){var p=ProblemDetail.forStatusAndDetail(status,reason);p.setTitle(status.getReasonPhrase());p.setProperty("reasonCode",reason);return ResponseEntity.status(status).body(p);}
}
